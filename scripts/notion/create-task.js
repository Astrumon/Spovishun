'use strict';

const http = require('./lib/notion-http');
const { loadToken } = require('./lib/load-token');
const { DATABASE_ID } = require('./lib/constants');

const VALID_PRIORITIES = ['High', 'Medium', 'Low'];

function readStdin() {
  return new Promise((resolve, reject) => {
    let data = '';
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', chunk => { data += chunk; });
    process.stdin.on('end', () => resolve(data));
    process.stdin.on('error', reject);
  });
}

async function main() {
  const token = loadToken();
  if (!token) {
    process.stderr.write('Error: NOTION_TOKEN or NOTION_SKILLS_TOKEN is required\n');
    process.exit(2);
  }

  const raw = await readStdin();
  let input;
  try {
    input = JSON.parse(raw);
  } catch {
    process.stderr.write('Error: invalid JSON on stdin\n');
    process.exit(1);
  }

  const { title, priority, content, icon } = input;

  if (!title || typeof title !== 'string' || !title.trim()) {
    process.stderr.write('Error: "title" is required and must be a non-empty string\n');
    process.exit(1);
  }
  if (!VALID_PRIORITIES.includes(priority)) {
    process.stderr.write(`Error: "priority" must be one of: ${VALID_PRIORITIES.join(', ')}\n`);
    process.exit(1);
  }
  if (content !== undefined && typeof content !== 'string') {
    process.stderr.write('Error: "content" must be a string\n');
    process.exit(1);
  }

  const body = {
    parent: { database_id: DATABASE_ID },
    properties: {
      Name: { title: [{ type: 'text', text: { content: title.trim() } }] },
      Priority: { select: { name: priority } },
      Status: { status: { name: 'To do' } },
    },
    children: content
      ? [{ object: 'block', type: 'paragraph', paragraph: { rich_text: [{ type: 'text', text: { content } }] } }]
      : [],
  };

  if (icon && typeof icon === 'string') {
    body.icon = { type: 'emoji', emoji: icon };
  }

  const result = await http.post(token, '/v1/pages', body);

  if (result?.object === 'error') {
    process.stderr.write(`Notion API error: ${result.message || result.code}\n`);
    process.exit(1);
  }

  process.stdout.write(JSON.stringify({ id: result.id, url: result.url }) + '\n');
}

main().catch(err => {
  process.stderr.write(`Error: ${err.message}\n`);
  process.exit(1);
});
