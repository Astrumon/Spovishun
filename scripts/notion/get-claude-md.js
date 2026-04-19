'use strict';

const http = require('./lib/notion-http');
const { loadToken } = require('./lib/load-token');
const { CLAUDE_MD_PAGE_ID } = require('./lib/constants');
const cache = require('./lib/cache');
const { extractBlocks } = require('./lib/format-task');

const CACHE_KEY = 'claude-md';
const CACHE_TTL_MS = 3_600_000;

async function main() {
  const token = loadToken();
  if (!token) {
    process.stderr.write('Error: NOTION_TOKEN or NOTION_SKILLS_TOKEN is required\n');
    process.exit(2);
  }

  const cached = cache.get(CACHE_KEY, CACHE_TTL_MS);
  if (cached) {
    process.stdout.write(cached);
    return;
  }

  const blocksResult = await http.get(token, `/v1/blocks/${CLAUDE_MD_PAGE_ID}/children?page_size=100`);

  if (blocksResult?.object === 'error') {
    process.stderr.write(`Notion API error: ${blocksResult.message || blocksResult.code}\n`);
    process.exit(1);
  }

  const text = extractBlocks(blocksResult?.results || []);
  cache.set(CACHE_KEY, text);
  process.stdout.write(text);
}

main().catch(err => {
  process.stderr.write(`Error: ${err.message}\n`);
  process.exit(1);
});
