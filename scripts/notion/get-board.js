'use strict';

const http = require('./lib/notion-http');
const { loadToken } = require('./lib/load-token');
const { DATABASE_ID } = require('./lib/constants');
const { queryByPriorityTier } = require('./lib/query-tasks');
const { richText } = require('./lib/format-task');
const { deriveBranchFromName } = require('./lib/extract-branch');

const VALID_STATUSES = ['Not started', 'To do', 'In progress', 'Done'];
const VALID_FORMATS = ['json', 'md', 'text'];

function parseArgs(argv) {
  let priorityTier = false;
  let latest = false;
  let status = 'To do';
  let format = 'json';
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--priority-tier') { priorityTier = true; }
    else if (argv[i] === '--latest') { latest = true; }
    else if (argv[i] === '--status' && argv[i + 1]) { status = argv[++i]; }
    else if (argv[i].startsWith('--format=')) { format = argv[i].slice(9); }
    else if (argv[i] === '--format' && argv[i + 1]) { format = argv[++i]; }
  }
  return { priorityTier, latest, status, format };
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

function renderMd(tasks) {
  if (tasks.length === 0) return '*(no tasks)*';
  const rows = tasks.map(t => `| ${t.title} | ${t.status ?? ''} | ${t.priority ?? ''} |`);
  return ['| Title | Status | Priority |', '|-------|--------|----------|', ...rows].join('\n');
}

function renderText(tasks) {
  if (tasks.length === 0) return '(no tasks)';
  const pad = (s, n) => (s ?? '').padEnd(n);
  const maxTitle = Math.max(5, ...tasks.map(t => t.title.length));
  const maxStatus = Math.max(6, ...tasks.map(t => (t.status ?? '').length));
  const maxPriority = Math.max(8, ...tasks.map(t => (t.priority ?? '').length));
  const header = `${pad('Title', maxTitle)}  ${pad('Status', maxStatus)}  Priority`;
  const sep = `${'-'.repeat(maxTitle)}  ${'-'.repeat(maxStatus)}  --------`;
  const rows = tasks.map(t =>
    `${pad(t.title, maxTitle)}  ${pad(t.status ?? '', maxStatus)}  ${t.priority ?? ''}`
  );
  return [header, sep, ...rows].join('\n');
}

async function main() {
  const token = loadToken();
  if (!token) {
    process.stderr.write('Error: NOTION_TOKEN or NOTION_SKILLS_TOKEN is required\n');
    process.exit(2);
  }

  const { priorityTier, latest, status, format } = parseArgs(process.argv.slice(2));

  if (!latest && !VALID_STATUSES.includes(status)) {
    process.stderr.write(`Error: invalid status "${status}". Valid: ${VALID_STATUSES.join(', ')}\n`);
    process.exit(1);
  }

  if (!VALID_FORMATS.includes(format)) {
    process.stderr.write(`Error: invalid format "${format}". Valid: ${VALID_FORMATS.join(', ')}\n`);
    process.exit(1);
  }

  let pages;

  if (latest) {
    const result = await http.post(token, `/v1/databases/${DATABASE_ID}/query`, {
      sorts: [{ timestamp: 'created_time', direction: 'descending' }],
      page_size: 10,
    });
    if (result?.object === 'error') {
      process.stderr.write(`Notion API error: ${result.message || result.code}\n`);
      process.exit(1);
    }
    pages = result?.results || [];
  } else if (priorityTier) {
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

  const tasks = pages.map(mapPage);

  if (format === 'md') {
    process.stdout.write(renderMd(tasks) + '\n');
  } else if (format === 'text') {
    process.stdout.write(renderText(tasks) + '\n');
  } else {
    process.stdout.write(JSON.stringify(tasks) + '\n');
  }
}

main().catch(err => {
  process.stderr.write(`Error: ${err.message}\n`);
  process.exit(1);
});
