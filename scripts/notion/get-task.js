'use strict';

const http = require('./lib/notion-http');
const { loadToken } = require('./lib/load-token');
const { DATABASE_ID } = require('./lib/constants');
const { richText, extractBlocks } = require('./lib/format-task');
const { extractBranchFromBlocks, deriveBranchFromName } = require('./lib/extract-branch');
const { toDashed } = require('./lib/page-id');

const VALID_FORMATS = ['json', 'md'];

function parseArgs(argv) {
  let format = 'json';
  const positional = [];
  for (let i = 0; i < argv.length; i++) {
    if (argv[i].startsWith('--format=')) { format = argv[i].slice(9); }
    else if (argv[i] === '--format' && argv[i + 1]) { format = argv[++i]; }
    else { positional.push(argv[i]); }
  }
  return { format, arg: positional[0] };
}

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

function renderMd(task) {
  const meta = [task.status, task.priority, task.branch].filter(Boolean).join(' | ');
  const parts = [`# ${task.title}`];
  if (meta) parts.push(meta);
  if (task.content) parts.push('---', task.content);
  return parts.join('\n\n');
}

async function main() {
  const token = loadToken();
  if (!token) {
    process.stderr.write('Error: NOTION_TOKEN or NOTION_SKILLS_TOKEN is required\n');
    process.exit(2);
  }

  const { format, arg } = parseArgs(process.argv.slice(2));

  if (!arg) {
    process.stderr.write('Usage: get-task.js <pageId | spovishun-N> [--format=json|md]\n');
    process.exit(1);
  }

  if (!VALID_FORMATS.includes(format)) {
    process.stderr.write(`Error: invalid format "${format}". Valid: ${VALID_FORMATS.join(', ')}\n`);
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

  if (format === 'md') {
    process.stdout.write(renderMd(task) + '\n');
  } else {
    process.stdout.write(JSON.stringify(task) + '\n');
  }
}

main().catch(err => {
  process.stderr.write(`Error: ${err.message}\n`);
  process.exit(1);
});
