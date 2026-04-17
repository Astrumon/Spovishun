#!/usr/bin/env node
/**
 * Finds a Notion task by spovishun-{TASK_N} in the Name property
 * and updates its status to "Done".
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

async function main() {
  const queryResult = await notionRequest('POST', `/v1/databases/${dbId}/query`, {
    filter: { property: 'Name', title: { contains: `spovishun-${taskN}` } },
    page_size: 5
  });

  const pages = queryResult?.results || [];

  if (pages.length === 0) {
    process.stdout.write(`No Notion task found for spovishun-${taskN}\n`);
    process.exit(0);
  }

  if (pages.length > 1) {
    const names = pages.map(p => (p.properties?.Name?.title || []).map(t => t.plain_text).join('')).join(', ');
    process.stderr.write(`Duplicate tasks matched: ${names}\n`);
    process.exit(1);
  }

  const page = pages[0];
  const name = (page.properties?.Name?.title || []).map(t => t.plain_text).join('');
  const status = page.properties?.Status?.status?.name;

  if (status === 'Done') {
    process.stdout.write(`Already Done, skipping: ${name}\n`);
    process.exit(0);
  }

  const patch = await notionRequest('PATCH', `/v1/pages/${page.id}`, {
    properties: { Status: { status: { name: 'Done' } } }
  });

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
