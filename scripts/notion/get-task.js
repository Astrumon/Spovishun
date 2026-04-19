'use strict';

const http = require('./lib/notion-http');
const { loadToken } = require('./lib/load-token');
const { DATABASE_ID } = require('./lib/constants');
const { richText, extractBlocks } = require('./lib/format-task');
const { extractBranchFromBlocks, deriveBranchFromName } = require('./lib/extract-branch');
const { toDashed } = require('./lib/page-id');

async function resolvePageId(token, arg) {
  if (/^spovishun-\d+$/i.test(arg)) {
    const result = await http.post(token, `/v1/databases/${DATABASE_ID}/query`, {
      filter: { property: 'Name', title: { contains: arg } },
      page_size: 1,
    });
    if (result?.object === 'error') {
      process.stderr.write(`Notion API error: ${result.message || result.code}\n`);
      process.exit(1);
    }
    const page = result?.results?.[0];
    if (!page) {
      process.stderr.write(`Error: task "${arg}" not found\n`);
      process.exit(1);
    }
    return page.id;
  }
  return toDashed(arg);
}

async function main() {
  const token = loadToken();
  if (!token) {
    process.stderr.write('Error: NOTION_TOKEN or NOTION_SKILLS_TOKEN is required\n');
    process.exit(2);
  }

  const arg = process.argv[2];
  if (!arg) {
    process.stderr.write('Usage: get-task.js <pageId | spovishun-N>\n');
    process.exit(1);
  }

  const pageId = await resolvePageId(token, arg);

  const [page, blocksResult] = await Promise.all([
    http.get(token, `/v1/pages/${pageId}`),
    http.get(token, `/v1/blocks/${pageId}/children?page_size=100`),
  ]);

  if (page?.object === 'error') {
    process.stderr.write(`Notion API error: ${page.message || page.code}\n`);
    process.exit(1);
  }

  const blocks = blocksResult?.results || [];
  const props = page.properties || {};
  const title = richText(props.Name?.title);

  const task = {
    id: page.id,
    title,
    status: props.Status?.status?.name ?? null,
    branch: extractBranchFromBlocks(blocks) ?? deriveBranchFromName(title),
    priority: props.Priority?.select?.name ?? null,
    content: extractBlocks(blocks),
  };

  process.stdout.write(JSON.stringify(task) + '\n');
}

main().catch(err => {
  process.stderr.write(`Error: ${err.message}\n`);
  process.exit(1);
});
