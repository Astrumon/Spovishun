#!/usr/bin/env node
/**
 * Finds a Notion task by spovishun-{TASK_N} in the Name property, sets its
 * Status to "Done" and — on Board v2 (Scrum), where a Stage select exists —
 * moves it to Stage "Archive" so the closed task leaves the active Sprint view.
 * On a board without a Stage property the Stage update is skipped silently.
 *
 * The task number is matched exactly (a "-{N}" not followed by another digit),
 * so closing task 8 never matches task 80.
 *
 * Required env vars:
 *   NOTION_TOKEN  — integration token
 *   NOTION_DB_ID  — database ID (no dashes)
 *   TASK_N        — task number (e.g. "58")
 */

const https = require('https');

const token = process.env.NOTION_TOKEN;
const dbId = process.env.NOTION_DB_ID;
const taskN = process.env.TASK_N;

if (!token || !dbId || !taskN) {
  process.stderr.write('Missing required env vars: NOTION_TOKEN, NOTION_DB_ID, TASK_N\n');
  process.exit(1);
}

// TASK_N is interpolated into a RegExp below; restrict it to digits so it can
// never inject regex metacharacters (defense-in-depth — it is normally derived
// from a branch name in CI).
if (!/^\d+$/.test(taskN)) {
  process.stderr.write(`Invalid TASK_N "${taskN}": expected a task number (digits only)\n`);
  process.exit(1);
}

function notionRequest(method, apiPath, body) {
  return new Promise((resolve, reject) => {
    const bodyStr = body ? JSON.stringify(body) : null;
    const req = https.request({
      hostname: 'api.notion.com',
      path: apiPath,
      method,
      headers: {
        'Authorization': `Bearer ${token}`,
        'Notion-Version': '2022-06-28',
        'Content-Type': 'application/json',
        ...(bodyStr && { 'Content-Length': Buffer.byteLength(bodyStr) })
      }
    }, (res) => {
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

// Whole-token match: "spovishun-8" matches "…spovishun-8:" / "…spovishun-8-"
// but not "spovishun-80". Notion's title filter has no regex/word-boundary
// support, so the query narrows by substring and JS enforces the exact number.
const TASK_NUMBER_RE = new RegExp(`spovishun-${taskN}(?![0-9])`, 'i');

function pageName(page) {
  return (page.properties?.Name?.title || []).map(t => t.plain_text).join('');
}

async function main() {
  const queryResult = await notionRequest('POST', `/v1/databases/${dbId}/query`, {
    filter: { property: 'Name', title: { contains: `spovishun-${taskN}` } },
    page_size: 20
  });

  const pages = (queryResult?.results || []).filter(p => TASK_NUMBER_RE.test(pageName(p)));

  if (pages.length === 0) {
    process.stdout.write(`No Notion task found for spovishun-${taskN}\n`);
    process.exit(0);
  }

  if (pages.length > 1) {
    process.stderr.write(`Duplicate tasks matched: ${pages.map(pageName).join(', ')}\n`);
    process.exit(1);
  }

  const page = pages[0];
  const name = pageName(page);
  const status = page.properties?.Status?.status?.name;
  const hasStage = 'Stage' in (page.properties || {});
  const stage = page.properties?.Stage?.select?.name;

  if (status === 'Done' && (!hasStage || stage === 'Archive')) {
    process.stdout.write(`Already closed, skipping: ${name}\n`);
    process.exit(0);
  }

  const properties = { Status: { status: { name: 'Done' } } };
  if (hasStage) properties.Stage = { select: { name: 'Archive' } };

  const patch = await notionRequest('PATCH', `/v1/pages/${page.id}`, { properties });

  if (patch?.object === 'error') {
    process.stderr.write(`Failed to update task: ${JSON.stringify(patch)}\n`);
    process.exit(1);
  }

  process.stdout.write(`Closed: ${name}\n`);
}

main().catch(err => {
  process.stderr.write(`Error: ${err.message}\n`);
  process.exit(1);
});
