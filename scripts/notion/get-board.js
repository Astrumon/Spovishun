'use strict';

const http = require('./lib/notion-http');
const { loadToken } = require('./lib/load-token');
const { DATABASE_ID } = require('./lib/constants');
const { queryByPriorityTier } = require('./lib/query-tasks');
const { richText } = require('./lib/format-task');
const { deriveBranchFromName } = require('./lib/extract-branch');

const VALID_STATUSES = ['Not started', 'To do', 'In progress', 'Done'];

function parseArgs(argv) {
  let priorityTier = false;
  let status = 'To do';
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--priority-tier') { priorityTier = true; }
    else if (argv[i] === '--status' && argv[i + 1]) { status = argv[++i]; }
  }
  return { priorityTier, status };
}

function mapPage(page) {
  const props = page.properties || {};
  const title = richText(props.Name?.title);
  return {
    id: page.id,
    title,
    status: props.Status?.status?.name ?? null,
    branch: deriveBranchFromName(title),
    priority: props.Priority?.select?.name ?? null,
  };
}

async function main() {
  const token = loadToken();
  if (!token) {
    process.stderr.write('Error: NOTION_TOKEN or NOTION_SKILLS_TOKEN is required\n');
    process.exit(2);
  }

  const { priorityTier, status } = parseArgs(process.argv.slice(2));

  if (!VALID_STATUSES.includes(status)) {
    process.stderr.write(`Error: invalid status "${status}". Valid: ${VALID_STATUSES.join(', ')}\n`);
    process.exit(1);
  }

  let pages;

  if (priorityTier) {
    const { candidates } = await queryByPriorityTier(http, token, status, new Set());
    pages = candidates;
  } else {
    const result = await http.post(token, `/v1/databases/${DATABASE_ID}/query`, {
      filter: { property: 'Status', status: { equals: status } },
      sorts: [{ timestamp: 'created_time', direction: 'ascending' }],
    });
    if (result?.object === 'error') {
      process.stderr.write(`Notion API error: ${result.message || result.code}\n`);
      process.exit(1);
    }
    pages = result?.results || [];
  }

  process.stdout.write(JSON.stringify(pages.map(mapPage)) + '\n');
}

main().catch(err => {
  process.stderr.write(`Error: ${err.message}\n`);
  process.exit(1);
});
