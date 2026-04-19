#!/usr/bin/env node
/**
 * Triple-mode hook:
 *
 * 1. UserPromptSubmit — injects active Notion task context into the prompt.
 *    On START_TASK_TRIGGERS: shows an interactive task picker directive for Claude
 *    to present via AskUserQuestion.
 *    Other triggers: cache-first context injection.
 *
 * 2. PostToolUse (ExitPlanMode) — auto-saves the approved plan.
 *    Invoked as: node notion-task-inject.js --post-exit-plan
 *
 * 3. CLI apply-pick — applies a task selection (creates branch, writes context).
 *    Invoked as: node notion-task-inject.js --apply-pick <pageId> [flags]
 *    Flags: --from-not-started  move task from Not started → To do first
 *           --no-switch         create branch without git checkout (2nd+ parallel task)
 *           --force             bypass git conflict check
 *    Called by Claude after user picks task(s) via AskUserQuestion.
 *    Exits 1 on error so Claude can surface it; all other modes exit 0.
 *
 * State files:
 *   .dev-context/{branch}_prd/branch.txt      — exact branch name
 *   .dev-context/{branch}_prd/context.md      — cached task text from Notion
 *   .dev-context/{branch}_prd/plan.md         — approved plan (auto-saved on ExitPlanMode)
 *   .dev-context/{branch}_prd/session.lock    — "{ppid}:{timestamp}" dedup guard
 *   .dev-context/selected-tasks.json          — active tasks shared across Claude instances
 *
 * Requires: NOTION_SKILLS_TOKEN (or NOTION_TOKEN) in env or .env file.
 */

const https = require('https');
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const DATABASE_ID = '3193462f68a980d69ec9c7ccc6329b88';
const NOTION_VERSION = '2022-06-28';
const DEV_CONTEXT_DIR = '.dev-context';
const SESSION_TTL_MS = 12 * 60 * 60 * 1000; // 12 hours
const SELECTED_TASKS_FILE = '.dev-context/selected-tasks.json';
const SELECTED_TASKS_VERSION = 1;
const PICKER_TIER_LIMIT = 5;
const PRIORITY_TIERS = ['High', 'Medium', 'Low'];

const TRIGGER_WORDS = [
  // English
  'implement', 'refactor',
  // Ukrainian
  'реалізуй', 'розроби', 'задача', 'таск', 'фіча'
];

const START_TASK_TRIGGERS = ['start new task', 'почати нову задачу', 'беру нову задачу'];

const REFRESH_TRIGGERS = ['reread task', 'update task context', 'оновити контекст задачі', 'перечитати задачу'];

// ─── Token + Notion request ───────────────────────────────────────────────────

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

// Returns { candidates, tier } for the highest priority tier with results.
// Cascades High → Medium → Low via separate API calls — avoids fetching all tasks.
async function queryByPriorityTier(token, statusFilter, excludePageIds) {
  for (const tier of PRIORITY_TIERS) {
    const result = await notionRequest(token, 'POST', `/v1/databases/${DATABASE_ID}/query`, {
      filter: {
        and: [
          { property: 'Status', status: { equals: statusFilter } },
          { property: 'Priority', select: { equals: tier } }
        ]
      },
      sorts: [{ timestamp: 'created_time', direction: 'ascending' }],
      page_size: PICKER_TIER_LIMIT
    });
    if (result?.object === 'error') continue;
    const candidates = (result?.results || []).filter(p => !excludePageIds.has(p.id.replace(/-/g, '')));
    if (candidates.length > 0) return { candidates, tier };
  }
  // Fallback: no priority filter
  const result = await notionRequest(token, 'POST', `/v1/databases/${DATABASE_ID}/query`, {
    filter: { property: 'Status', status: { equals: statusFilter } },
    page_size: PICKER_TIER_LIMIT
  });
  const candidates = (result?.results || []).filter(p => !excludePageIds.has(p.id.replace(/-/g, '')));
  return { candidates, tier: null };
}

// ─── Block / branch extraction ────────────────────────────────────────────────

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

function extractTaskNumber(name) {
  const m = name.match(/spovishun-(\d+)/i);
  return m ? parseInt(m[1], 10) : null;
}

function deriveBranchFromName(name) {
  const numMatch = name.match(/spovishun-(\d+)/i);
  if (!numMatch) return null;
  const taskNum = numMatch[1];
  const slug = name
    .replace(/^feature\/spovishun-\d+:\s*/i, '')
    .toLowerCase()
    .replace(/[^a-z0-9\s-]/g, '')
    .trim()
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .split('-').slice(0, 3).join('-');
  return `feature/spovishun-${taskNum}-${slug}`;
}

// ─── Git helpers ──────────────────────────────────────────────────────────────

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

function gitSetupBranch(branch) {
  try {
    execSync(`git rev-parse --verify "${branch}"`, { stdio: 'pipe' });
    execSync(`git checkout "${branch}"`, { stdio: 'pipe' });
    return { ok: true, message: `Switched to existing branch: ${branch}` };
  } catch {
    return gitCheckoutFromDevelop(branch);
  }
}

function gitCreateBranchOnly(branch) {
  try {
    execSync(`git rev-parse --verify "${branch}"`, { stdio: 'pipe' });
    return { ok: true, message: `Branch already exists: ${branch}` };
  } catch {
    try {
      execSync('git fetch origin develop --quiet', { stdio: 'pipe' });
    } catch { /* no remote, ignore */ }
    try {
      execSync(`git branch "${branch}" develop`, { stdio: 'pipe' });
      return { ok: true, message: `Created branch (no checkout): ${branch}` };
    } catch (err) {
      return { ok: false, message: err.message };
    }
  }
}

function conflictCheck(newBranch, existingTasks, force) {
  const getDiff = (branch) => {
    try {
      const count = parseInt(
        execSync(`git rev-list develop..${branch} --count`, { stdio: 'pipe' }).toString().trim(),
        10
      );
      if (isNaN(count) || count === 0) return null;
      const out = execSync(`git diff --name-only develop...${branch}`, { stdio: 'pipe' }).toString();
      return new Set(out.split('\n').filter(Boolean));
    } catch {
      return null;
    }
  };

  const newFiles = getDiff(newBranch);
  const disclaimer = existingTasks.length > 0
    ? 'DISCLAIMER: Parallel tasks active; run each in a separate Claude Code instance.'
    : '';

  if (!newFiles) return { conflict: false, disclaimer };

  for (const task of existingTasks) {
    if (task.branch === newBranch) continue;
    const otherFiles = getDiff(task.branch);
    if (!otherFiles) continue;
    const intersection = [...newFiles].filter(f => otherFiles.has(f));
    if (intersection.length > 0 && !force) {
      return {
        conflict: true,
        msg: `CONFLICT: overlapping files between ${newBranch} and ${task.branch}: [${intersection.join(', ')}]. Retry with --force to bypass.`,
        disclaimer
      };
    }
  }
  return { conflict: false, disclaimer };
}

// ─── Dev context folder ───────────────────────────────────────────────────────

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

// ─── Selected tasks state ─────────────────────────────────────────────────────

function loadSelectedTasks() {
  const filePath = path.join(process.cwd(), SELECTED_TASKS_FILE);
  try {
    const raw = fs.readFileSync(filePath, 'utf8');
    const data = JSON.parse(raw);
    return Array.isArray(data.tasks) ? data.tasks : [];
  } catch {
    return [];
  }
}

function saveSelectedTasks(tasks) {
  const filePath = path.join(process.cwd(), SELECTED_TASKS_FILE);
  ensureDir(path.dirname(filePath));
  fs.writeFileSync(filePath, JSON.stringify({ version: SELECTED_TASKS_VERSION, tasks }, null, 2), 'utf8');
}

// ─── Task Picker ──────────────────────────────────────────────────────────────

async function runPicker(token, currentBranch, isForce) {
  // 1. Validate selected-tasks.json — clear entire file if any task is no longer active
  let selectedTasks = loadSelectedTasks();
  if (selectedTasks.length > 0) {
    for (const task of selectedTasks) {
      const page = await notionRequest(token, 'GET', `/v1/pages/${task.pageId}`, null);
      const status = page?.properties?.Status?.status?.name;
      if (!['To do', 'In progress'].includes(status)) {
        saveSelectedTasks([]);
        selectedTasks = [];
        break;
      }
    }
  }

  // 2. If on an active task branch, inject from cache (user is resuming work)
  if (currentBranch && currentBranch !== 'develop' && currentBranch !== 'main') {
    const activeEntry = selectedTasks.find(t => t.branch === currentBranch);
    if (activeEntry) {
      const ctxDir = getContextDir(currentBranch);
      const contextFile = path.join(ctxDir, 'context.md');
      if (fs.existsSync(contextFile)) {
        await notionRequest(token, 'PATCH', `/v1/pages/${activeEntry.pageId}`, {
          properties: { Status: { status: { name: 'In progress' } } }
        }).catch(() => {});
        const context = fs.readFileSync(contextFile, 'utf8');
        const planFile = path.join(ctxDir, 'plan.md');
        const plan = fs.existsSync(planFile) ? fs.readFileSync(planFile, 'utf8') : null;
        writeSessionLock(path.join(ctxDir, 'session.lock'));
        output(buildSystemPrompt(context, plan, '\n**Git:** Already on active task branch — skipping checkout', true));
        return;
      }
    }
  }

  // 3. Query candidate tasks by priority tier (High → Medium → Low), excluding already-selected
  const selectedPageIds = new Set(selectedTasks.map(t => t.pageId));

  let source = 'toDo';
  let { candidates, tier } = await queryByPriorityTier(token, 'To do', selectedPageIds);

  if (candidates.length === 0) {
    source = 'notStarted';
    ({ candidates, tier } = await queryByPriorityTier(token, 'Not started', selectedPageIds));
  }

  if (candidates.length === 0) {
    output(buildSystemPrompt(
      '## 🪝 No Tasks Available\n\nNo "To do" or "Not started" tasks found in Notion.',
      null, null, false
    ));
    return;
  }

  // 4. Build option metadata (candidates are already the correct priority tier, ordered by created_time)
  const options = candidates.map(page => {
    const name = (page.properties?.Name?.title || []).map(t => t.plain_text).join('') || 'Unknown';
    const taskNum = extractTaskNumber(name) || '?';
    const priority = tier || page.properties?.Priority?.select?.name || 'Normal';
    const pageId = page.id.replace(/-/g, '');
    const displayName = name.replace(/^feature\/spovishun-\d+:\s*/i, '').trim();
    return { taskNum, name, displayName, priority, pageId };
  });

  // 5. Format picker directive for Claude
  const parallelNote = selectedTasks.length > 0
    ? `\n⚠️ **${selectedTasks.length} task(s) currently active**: ${selectedTasks.map(t => `spovishun-${t.taskNumber}`).join(', ')}. Adding more = parallel execution across Claude Code instances.`
    : '';

  const sourceNote = source === 'notStarted'
    ? '\n> 📋 No "To do" tasks found — showing "Not started". Selected task(s) will be moved to "To do" automatically.'
    : '';

  const applyFlags = [
    source === 'notStarted' ? '--from-not-started' : '',
    isForce ? '--force' : ''
  ].filter(Boolean).join(' ');
  const applyFlagsSuffix = applyFlags ? ` ${applyFlags}` : '';

  const optionLines = options.map((o, i) =>
    `${i + 1}. **spovishun-${o.taskNum}** — ${o.displayName} *(Priority: ${o.priority})*\n   pageId: \`${o.pageId}\``
  ).join('\n');

  const aqOptions = options.map(o =>
    `     {label: "spovishun-${o.taskNum} — ${o.displayName}", value: "${o.pageId}"}`
  ).join(',\n');

  const directive = `## 🪝 Task Picker
${parallelNote}${sourceNote}

**Available tasks** (${source === 'notStarted' ? 'Not started' : 'To do'}):
${optionLines}

---
### REQUIRED NEXT ACTIONS (execute in order):
1. Call \`AskUserQuestion\`:
   \`\`\`
   question: "Оберіть задачі для початку роботи (можна вибрати декілька):"
   multiSelect: true
   options: [
${aqOptions},
     {label: "Відмінити", value: "cancel"}
   ]
   \`\`\`
2. If user picked **"Відмінити"** → inform user, stop.
3. For **each** selected pageId — run Bash sequentially:
   - 1st task:  \`node .claude/hooks/notion-task-inject.js --apply-pick <pageId>${applyFlagsSuffix}\`
   - 2nd+ task: \`node .claude/hooks/notion-task-inject.js --apply-pick <pageId>${applyFlagsSuffix} --no-switch\`
   If stderr starts with \`CONFLICT:\` → show user the conflicting files, ask: retry with \`--force\` or skip?
4. After all applies — if ≥2 tasks selected → show the DISCLAIMER line from stdout.
5. Run \`git checkout <branch-of-first-selected-task>\` if not already there.
6. Briefly confirm: task name(s), branch(es), and total active parallel tasks count.
7. Immediately invoke the \`notion-task-to-code\` skill with the first selected pageId to load task context and enter Plan Mode. Do not wait for user input.`;

  output(directive);
}

// ─── Apply Pick ───────────────────────────────────────────────────────────────

async function applyPickMain(token, pageId, { force, fromNotStarted, noSwitch }) {
  // Normalize pageId: compact (no dashes) → UUID with dashes for Notion API
  const notionPageId = pageId.length === 32
    ? `${pageId.slice(0, 8)}-${pageId.slice(8, 12)}-${pageId.slice(12, 16)}-${pageId.slice(16, 20)}-${pageId.slice(20)}`
    : pageId;

  // 1. Fetch page
  const page = await notionRequest(token, 'GET', `/v1/pages/${notionPageId}`, null);
  if (!page || page.object === 'error') {
    process.stderr.write(`[apply-pick] Failed to fetch page ${pageId}: ${JSON.stringify(page)}\n`);
    process.exit(1);
  }

  const name = (page.properties?.Name?.title || []).map(t => t.plain_text).join('') || 'Unknown';
  const status = page.properties?.Status?.status?.name;
  const cleanPageId = page.id.replace(/-/g, '');

  // 2. Handle status transition
  if (fromNotStarted) {
    const patch = await notionRequest(token, 'PATCH', `/v1/pages/${page.id}`, {
      properties: { Status: { status: { name: 'To do' } } }
    });
    if (patch?.object === 'error') {
      process.stderr.write(`[apply-pick] Failed to update status to To do: ${JSON.stringify(patch)}\n`);
      process.exit(1);
    }
  } else if (!['To do', 'In progress'].includes(status)) {
    process.stderr.write(`[apply-pick] Task status is "${status}", expected "To do" or "In progress"\n`);
    process.exit(1);
  }

  // 3. Fetch blocks to derive branch and content
  const blocksResult = await notionRequest(token, 'GET', `/v1/blocks/${cleanPageId}/children?page_size=100`, null);
  const allBlocks = blocksResult?.results || [];

  let taskBranch = extractBranchFromBlocks(allBlocks);
  if (!taskBranch) taskBranch = deriveBranchFromName(name);
  if (!taskBranch) {
    process.stderr.write(`[apply-pick] Cannot derive branch name from task: "${name}"\n`);
    process.exit(1);
  }

  const taskNumber = extractTaskNumber(name);

  // 4. Conflict check against other active tasks
  const selectedTasks = loadSelectedTasks();
  const otherTasks = selectedTasks.filter(t => t.pageId !== cleanPageId);
  const check = conflictCheck(taskBranch, otherTasks, force);
  if (check.conflict) {
    process.stderr.write(check.msg + '\n');
    process.exit(1);
  }

  // 5. Git setup
  const gitResult = noSwitch
    ? gitCreateBranchOnly(taskBranch)
    : gitSetupBranch(taskBranch);

  // 6. Write context files
  const contentBlocks = allBlocks.filter(b => b.type !== 'toggle');
  const content = extractBlocks(contentBlocks);
  const ctxDir = getContextDir(taskBranch);
  ensureDir(ctxDir);
  fs.writeFileSync(path.join(ctxDir, 'context.md'), `## 🪝 Active Task (Notion)\n**${name}**\n\n${content}`, 'utf8');
  fs.writeFileSync(path.join(ctxDir, 'branch.txt'), taskBranch, 'utf8');
  writeSessionLock(path.join(ctxDir, 'session.lock'));

  // 7. Update Notion status to In progress
  await notionRequest(token, 'PATCH', `/v1/pages/${page.id}`, {
    properties: { Status: { status: { name: 'In progress' } } }
  });

  // 8. Upsert into selected-tasks.json
  const updated = selectedTasks.filter(t => t.pageId !== cleanPageId);
  updated.push({ pageId: cleanPageId, taskNumber, name, branch: taskBranch, addedAt: Date.now(), status: 'In progress' });
  saveSelectedTasks(updated);

  // 9. Output result
  if (check.disclaimer) process.stdout.write(check.disclaimer + '\n');
  process.stdout.write(`OK: spovishun-${taskNumber} activated on ${taskBranch}\n`);
  if (!gitResult.ok) {
    process.stderr.write(`[apply-pick] Git warning: ${gitResult.message}\n`);
  }
}

// ─── PostToolUse: ExitPlanMode — auto-save plan ───────────────────────────────

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

// ─── Main ─────────────────────────────────────────────────────────────────────

async function main() {
  let raw = '';
  process.stdin.setEncoding('utf8');
  for await (const chunk of process.stdin) raw += chunk;

  let data;
  try { data = JSON.parse(raw); } catch { process.exit(0); }

  const prompt = (data.prompt || '').toLowerCase();

  const isStartTask = START_TASK_TRIGGERS.some(t => prompt.includes(t));
  const isRefresh = REFRESH_TRIGGERS.some(t => prompt.includes(t));
  const isForce = prompt.includes('--force');
  const hasTrigger = isStartTask || isRefresh || TRIGGER_WORDS.some(word => prompt.includes(word));

  if (!hasTrigger) process.exit(0);

  const currentBranch = getCurrentBranch();

  // ── START_TASK_TRIGGERS → interactive picker ─────────────────────────────────
  if (isStartTask) {
    const token = loadToken();
    if (!token) {
      process.stderr.write('[notion-task-inject] NOTION_TOKEN not set, skipping picker\n');
      process.exit(0);
    }
    try {
      await runPicker(token, currentBranch, isForce);
    } catch (err) {
      process.stderr.write(`[notion-task-inject] Picker error: ${err.message}\n`);
    }
    process.exit(0);
  }

  // ── Try to serve from cache (TRIGGER_WORDS / REFRESH) ───────────────────────
  if (!isRefresh && currentBranch && currentBranch !== 'develop' && currentBranch !== 'main') {
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

      output(buildSystemPrompt(context, plan, null, false));
      process.exit(0);
    }
  }

  // ── Fetch from Notion (TRIGGER_WORDS / REFRESH) ──────────────────────────────
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
    if (!taskBranch) taskBranch = deriveBranchFromName(name);

    // Determine cache folder
    const cacheBranch = currentBranch && currentBranch !== 'develop' && currentBranch !== 'main'
      ? currentBranch
      : taskBranch;

    // Save to cache
    if (cacheBranch) {
      const ctxDir = getContextDir(cacheBranch);
      ensureDir(ctxDir);
      const contextMd = `## 🪝 Active Task (Notion)\n**${name}**\n\n${content}`;
      fs.writeFileSync(path.join(ctxDir, 'context.md'), contextMd, 'utf8');
      fs.writeFileSync(path.join(ctxDir, 'branch.txt'), cacheBranch, 'utf8');
      writeSessionLock(path.join(ctxDir, 'session.lock'));
    }

    let branchNote = '';
    if (isRefresh) {
      branchNote = '\n> 🔄 Context refreshed from Notion.';
    } else if (currentBranch && taskBranch && currentBranch !== taskBranch) {
      branchNote = `\n> ⚠️ Current branch: \`${currentBranch}\`. Task branch: \`${taskBranch}\`. Use "start new task" to switch and load full context.`;
    }

    // On refresh — also load plan.md if present
    let plan = null;
    if (isRefresh && cacheBranch) {
      const planFile = path.join(getContextDir(cacheBranch), 'plan.md');
      if (fs.existsSync(planFile)) plan = fs.readFileSync(planFile, 'utf8');
    }

    const contextText = `## 🪝 Active Task (Notion — In progress)\n**${name}**\n\n${content}`;
    output(buildSystemPrompt(contextText, plan, branchNote, false));
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

// ─── Entry point ──────────────────────────────────────────────────────────────

if (process.argv[2] === '--post-exit-plan') {
  runPostExitPlan();
} else if (process.argv[2] === '--apply-pick') {
  const pageId = process.argv[3];
  if (!pageId) {
    process.stderr.write('[apply-pick] Usage: --apply-pick <pageId> [--from-not-started] [--no-switch] [--force]\n');
    process.exit(1);
  }
  const token = loadToken();
  if (!token) {
    process.stderr.write('[apply-pick] NOTION_TOKEN not set\n');
    process.exit(1);
  }
  const force = process.argv.includes('--force');
  const fromNotStarted = process.argv.includes('--from-not-started');
  const noSwitch = process.argv.includes('--no-switch');
  applyPickMain(token, pageId, { force, fromNotStarted, noSwitch }).catch(err => {
    process.stderr.write(`[apply-pick] Error: ${err.message}\n`);
    process.exit(1);
  });
} else {
  main();
}
