#!/usr/bin/env node
/**
 * Dual-mode hook:
 *
 * 1. UserPromptSubmit — injects active Notion task context into the prompt.
 *    Caches context in .dev-context/{branch}_prd/ — Notion API called once per
 *    branch lifetime; subsequent sessions read from file (no network requests).
 *
 * 2. PostToolUse (ExitPlanMode) — auto-saves the approved plan.
 *    Invoked as: node notion-task-inject.js --post-exit-plan
 *    Reads plan from tool_input.plan and writes to .dev-context/{branch}_prd/plan.md.
 *    Plan is then injected automatically on every new session for that branch.
 *
 * Files in the task folder:
 *   branch.txt    — exact branch name
 *   context.md    — cached task text from Notion
 *   plan.md       — approved plan, auto-saved after ExitPlanMode
 *   session.lock  — "{ppid}:{timestamp}" of the current session (dedup injections)
 *
 * Requires: NOTION_SKILLS_TOKEN (or NOTION_TOKEN) in env or .env file.
 * Always exits with exit(0) — errors do not block work.
 */

const https = require('https');
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const DATABASE_ID = '3193462f68a980d69ec9c7ccc6329b88';
const NOTION_VERSION = '2022-06-28';
const DEV_CONTEXT_DIR = '.dev-context';
const SESSION_TTL_MS = 12 * 60 * 60 * 1000; // 12 hours

const TRIGGER_WORDS = [
  // English
  'implement', 'refactor',
  // Ukrainian
  'реалізуй', 'розроби', 'задача', 'таск', 'фіча'
];

const START_TASK_TRIGGERS = ['start new task', 'почати нову задачу', 'беру нову задачу'];

const REFRESH_TRIGGERS = ['reread task', 'update task context', 'оновити контекст задачі', 'перечитати задачу'];

// ─── Helpers ────────────────────────────────────────────────────────────────

function loadToken() {
  if (process.env.NOTION_SKILLS_TOKEN) return process.env.NOTION_SKILLS_TOKEN;
  if (process.env.NOTION_TOKEN) return process.env.NOTION_TOKEN;

  const envPath = path.join(process.cwd(), '.env');
  try {
    const content = fs.readFileSync(envPath, 'utf8');
    const tokenMatch = content.match(/^NOTION_TOKEN=(.+)$/m);
    if (tokenMatch) return tokenMatch[1].trim();
    const skillsMatch = content.match(/^NOTION_SKILLS_TOKEN=(.+)$/m);
    if (skillsMatch) return skillsMatch[1].trim();
  } catch {
    process.stderr.write(`[notion-task-inject] .env not found at ${envPath}\n`);
  }
  return null;
}

function notionRequest(token, method, apiPath, body) {
  return new Promise((resolve, reject) => {
    const bodyStr = body ? JSON.stringify(body) : null;
    const options = {
      hostname: 'api.notion.com',
      path: apiPath,
      method,
      headers: {
        'Authorization': `Bearer ${token}`,
        'Notion-Version': NOTION_VERSION,
        'Content-Type': 'application/json',
        ...(bodyStr && { 'Content-Length': Buffer.byteLength(bodyStr) })
      }
    };

    const req = https.request(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try { resolve(JSON.parse(data)); } catch { resolve(null); }
      });
    });

    req.on('error', reject);
    if (bodyStr) req.write(bodyStr);
    req.end();
  });
}

function richText(blocks) {
  return (blocks || []).map(rt => rt.plain_text).join('').trim();
}

function extractBlocks(blocks) {
  const lines = [];
  for (const block of blocks) {
    const type = block.type;
    const content = block[type];
    if (!content) continue;
    const text = (content.rich_text || []).map(rt => rt.plain_text).join('');
    if (type.startsWith('heading_')) {
      if (text) lines.push(`\n**${text}**`);
    } else if (type === 'paragraph') {
      if (text) lines.push(text);
    } else if (type === 'bulleted_list_item' || type === 'numbered_list_item') {
      if (text) lines.push(`- ${text}`);
    } else if (type === 'quote') {
      if (text) lines.push(`> ${text}`);
    }
  }
  return lines.join('\n').trim();
}

function extractBranchFromBlocks(blocks) {
  for (let i = 0; i < blocks.length; i++) {
    const block = blocks[i];
    const type = block.type;
    if (!type) continue;
    const content = block[type];
    if (!content) continue;
    const text = richText(content.rich_text);
    if (text.includes('Branch name') || text.includes('🌿')) {
      for (let j = i + 1; j < blocks.length && j <= i + 3; j++) {
        const next = blocks[j];
        if (!next || !next.type) continue;
        const nextContent = next[next.type];
        if (!nextContent) continue;
        const nextText = richText(nextContent.rich_text);
        if (nextText && nextText.startsWith('feature/')) return nextText.trim();
      }
    }
    if (text.startsWith('feature/spovishun-')) return text.trim();
  }
  return null;
}

function getCurrentBranch() {
  try {
    return execSync('git rev-parse --abbrev-ref HEAD', { stdio: 'pipe' }).toString().trim();
  } catch {
    return null;
  }
}

function gitCheckoutFromDevelop(branch) {
  try {
    execSync('git checkout develop', { stdio: 'pipe' });
    execSync('git pull origin develop', { stdio: 'pipe' });
    execSync(`git checkout -b "${branch}"`, { stdio: 'pipe' });
    return { ok: true, message: `Created and switched to: ${branch}` };
  } catch (err) {
    return { ok: false, message: err.message };
  }
}

// ─── Dev Context folder ──────────────────────────────────────────────────────

function branchToFolderName(branch) {
  return branch.replace(/\//g, '-') + '_prd';
}

function getContextDir(branch) {
  return path.join(process.cwd(), DEV_CONTEXT_DIR, branchToFolderName(branch));
}

/**
 * Checks whether the lock belongs to the current session.
 * Lock file format: "{ppid}:{timestamp}"
 * Guards against PID reuse on Windows: lock is considered invalid after SESSION_TTL_MS.
 */
function isCurrentSession(lockFile) {
  try {
    const content = fs.readFileSync(lockFile, 'utf8').trim();
    const [pidStr, tsStr] = content.split(':');
    const pid = parseInt(pidStr, 10);
    const ts = parseInt(tsStr, 10);

    // Lock older than TTL means a new session (guards against PID reuse)
    if (Date.now() - ts > SESSION_TTL_MS) return false;

    process.kill(pid, 0); // throws if process does not exist
    return true;
  } catch {
    return false;
  }
}

function writeSessionLock(lockFile) {
  const pid = process.ppid || process.pid;
  fs.writeFileSync(lockFile, `${pid}:${Date.now()}`, 'utf8');
}

function ensureDir(dir) {
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}


// ─── PostToolUse: ExitPlanMode — auto-save plan ──────────────────────────────

async function runPostExitPlan() {
  let raw = '';
  process.stdin.setEncoding('utf8');
  for await (const chunk of process.stdin) raw += chunk;

  let data;
  try { data = JSON.parse(raw); } catch { process.exit(0); }

  const planContent = data?.tool_input?.plan;
  if (!planContent) {
    process.stderr.write('[notion-task-inject] post-exit-plan: no plan in tool_input\n');
    process.exit(0);
  }

  const branch = getCurrentBranch();
  if (!branch || branch === 'develop' || branch === 'main') {
    process.stderr.write('[notion-task-inject] post-exit-plan: not on a feature branch\n');
    process.exit(0);
  }

  const ctxDir = getContextDir(branch);
  ensureDir(ctxDir);
  fs.writeFileSync(path.join(ctxDir, 'plan.md'), planContent, 'utf8');
  // Invalidate session lock so the next UserPromptSubmit in this session
  // re-injects context + plan instead of skipping via isCurrentSession().
  const lockFile = path.join(ctxDir, 'session.lock');
  if (fs.existsSync(lockFile)) fs.unlinkSync(lockFile);
  process.stderr.write(`[notion-task-inject] plan saved → .dev-context/${branchToFolderName(branch)}/plan.md\n`);
  process.exit(0);
}

// ─── Main ────────────────────────────────────────────────────────────────────

async function main() {
  let raw = '';
  process.stdin.setEncoding('utf8');
  for await (const chunk of process.stdin) raw += chunk;

  let data;
  try { data = JSON.parse(raw); } catch { process.exit(0); }

  const prompt = (data.prompt || '').toLowerCase();

  const isStartTask = START_TASK_TRIGGERS.some(t => prompt.includes(t));
  const isRefresh = REFRESH_TRIGGERS.some(t => prompt.includes(t));
  const hasTrigger = isStartTask || isRefresh || TRIGGER_WORDS.some(word => prompt.includes(word));

  if (!hasTrigger) process.exit(0);

  const currentBranch = getCurrentBranch();

  // ── Try to serve from cache (skip if refresh or start-task) ─────────────────
  if (!isRefresh && !isStartTask && currentBranch && currentBranch !== 'develop' && currentBranch !== 'main') {
    const ctxDir = getContextDir(currentBranch);
    const contextFile = path.join(ctxDir, 'context.md');
    const lockFile = path.join(ctxDir, 'session.lock');
    const planFile = path.join(ctxDir, 'plan.md');

    if (fs.existsSync(contextFile)) {
      // Already injected in this session — skip
      if (isCurrentSession(lockFile)) process.exit(0);

      // New session, same branch — inject from cache
      const context = fs.readFileSync(contextFile, 'utf8');
      const plan = fs.existsSync(planFile) ? fs.readFileSync(planFile, 'utf8') : null;
      writeSessionLock(lockFile);

      output(buildSystemPrompt(context, plan, null, isStartTask));
      process.exit(0);
    }
  }

  // ── Fetch from Notion ────────────────────────────────────────────────────
  const token = loadToken();
  if (!token) {
    process.stderr.write('[notion-task-inject] NOTION_SKILLS_TOKEN not set, skipping\n');
    process.exit(0);
  }

  try {
    // Include both statuses — task may already be In progress
    const queryResult = await notionRequest(token, 'POST', `/v1/databases/${DATABASE_ID}/query`, {
      filter: {
        or: [
          { property: 'Status', status: { equals: 'To do' } },
          { property: 'Status', status: { equals: 'In progress' } }
        ]
      },
      page_size: 1
    });

    const page = queryResult?.results?.[0];
    if (!page) process.exit(0);

    const name = (page.properties?.Name?.title || []).map(t => t.plain_text).join('') || 'Unknown';
    const pageId = page.id.replace(/-/g, '');

    const blocksResult = await notionRequest(token, 'GET', `/v1/blocks/${pageId}/children?page_size=100`, null);
    const allBlocks = blocksResult?.results || [];
    const contentBlocks = allBlocks.filter(b => b.type !== 'toggle');
    const content = extractBlocks(contentBlocks);

    // Derive task branch
    let taskBranch = extractBranchFromBlocks(allBlocks);
    if (!taskBranch) {
      const numMatch = name.match(/spovishun-(\d+)/i);
      if (numMatch) {
        const taskNum = numMatch[1];
        const slug = name
          .replace(/^feature\/spovishun-\d+:\s*/i, '')
          .toLowerCase()
          .replace(/[^a-z0-9\s-]/g, '')
          .trim()
          .replace(/\s+/g, '-')
          .replace(/-+/g, '-')
          .split('-').slice(0, 3).join('-');
        taskBranch = `feature/spovishun-${taskNum}-${slug}`;
      }
    }

    // On start-task: if already on the correct branch — read from cache
    if (isStartTask && taskBranch && currentBranch === taskBranch) {
      const ctxDir = getContextDir(taskBranch);
      const contextFile = path.join(ctxDir, 'context.md');
      const planFile = path.join(ctxDir, 'plan.md');
      if (fs.existsSync(contextFile)) {
        await notionRequest(token, 'PATCH', `/v1/pages/${page.id}`, {
          properties: { Status: { status: { name: 'In progress' } } }
        });
        const cachedContext = fs.readFileSync(contextFile, 'utf8');
        const plan = fs.existsSync(planFile) ? fs.readFileSync(planFile, 'utf8') : null;
        writeSessionLock(path.join(ctxDir, 'session.lock'));
        const branchNote = `\n**Git:** Already on \`${taskBranch}\` — skipping checkout`;
        output(buildSystemPrompt(cachedContext, plan, branchNote, isStartTask));
        process.exit(0);
      }
    }

    // Determine cache folder
    const cacheBranch = isStartTask && taskBranch
      ? taskBranch
      : (currentBranch && currentBranch !== 'develop' && currentBranch !== 'main'
          ? currentBranch
          : taskBranch);

    // Save to cache
    if (cacheBranch) {
      const ctxDir = getContextDir(cacheBranch);
      ensureDir(ctxDir);
      const contextMd = `## 🪝 Active Task (Notion)\n**${name}**\n\n${content}`;
      fs.writeFileSync(path.join(ctxDir, 'context.md'), contextMd, 'utf8');
      fs.writeFileSync(path.join(ctxDir, 'branch.txt'), cacheBranch, 'utf8');
      writeSessionLock(path.join(ctxDir, 'session.lock'));
    }

    // Handle start-task actions
    let branchNote = '';
    if (isStartTask) {
      await notionRequest(token, 'PATCH', `/v1/pages/${page.id}`, {
        properties: { Status: { status: { name: 'In progress' } } }
      });

      if (taskBranch) {
        if (currentBranch === taskBranch) {
          branchNote = `\n**Git:** Already on \`${taskBranch}\` — skipping checkout`;
        } else {
          const result = gitCheckoutFromDevelop(taskBranch);
          branchNote = result.ok
            ? `\n**Git:** ${result.message}`
            : `\n**Git error:** ${result.message}`;
        }
      }
    } else if (isRefresh) {
      branchNote = '\n> 🔄 Context refreshed from Notion.';
    } else if (currentBranch && taskBranch && currentBranch !== taskBranch) {
      branchNote = `\n> ⚠️ Current branch: \`${currentBranch}\`. Task branch: \`${taskBranch}\`. Use "start task" to switch and load full context.`;
    }

    // On refresh — also load plan.md if present
    let plan = null;
    if (isRefresh && cacheBranch) {
      const planFile = path.join(getContextDir(cacheBranch), 'plan.md');
      if (fs.existsSync(planFile)) plan = fs.readFileSync(planFile, 'utf8');
    }

    const contextText = `## 🪝 Active Task (Notion — In progress)\n**${name}**\n\n${content}`;
    output(buildSystemPrompt(contextText, plan, branchNote, isStartTask));
    process.exit(0);

  } catch (err) {
    process.stderr.write(`[notion-task-inject] Error: ${err.message}\n`);
    process.exit(0);
  }
}

function buildSystemPrompt(context, plan, branchNote, isStartTask) {
  const parts = [context];
  if (plan) parts.push(`\n---\n## 📋 Approved Plan\n${plan}`);
  if (branchNote) parts.push(branchNote);
  parts.push('\n---');

  let instruction;
  if (isStartTask && plan) {
    instruction = '✅ Plan already approved. Proceed directly with implementation — do NOT enter plan mode again.';
  } else if (isStartTask) {
    instruction = '⚠️ IMPORTANT: You MUST call the EnterPlanMode tool immediately before doing anything else. Build a detailed implementation plan based on the task above. Do NOT write any code until the plan is approved.';
  } else {
    instruction = '*Work within the scope of this task. Do not go beyond what is described.*';
  }

  parts.push(instruction);
  return parts.join('\n');
}

function output(systemPrompt) {
  process.stdout.write(JSON.stringify({
    hookSpecificOutput: {
      hookEventName: 'UserPromptSubmit',
      additionalContext: systemPrompt
    }
  }));
}

if (process.argv[2] === '--post-exit-plan') {
  runPostExitPlan();
} else {
  main();
}
