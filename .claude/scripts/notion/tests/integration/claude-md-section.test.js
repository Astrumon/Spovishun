'use strict';

const { describe, test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('child_process');
const os = require('os');
const fs = require('fs');
const path = require('path');
const { loadToken } = require('../../lib/load-token');

const SCRIPTS_DIR = path.join(__dirname, '..', '..');
const REPO_ROOT = path.join(SCRIPTS_DIR, '..', '..', '..');
const token = loadToken();
const SKIP_MSG = token ? undefined : 'NOTION_TOKEN not set — skipping integration test';

// Picks a query token from the first markdown heading (## ...) in `markdown`:
// the first whitespace-delimited word containing ≥3 letters, stripped to its
// letter characters. Returns null if no heading yields a usable token. Keeps
// the integration test independent of the page's language and exact wording.
function pickSectionQuery(markdown) {
  for (const line of markdown.split('\n')) {
    const heading = line.match(/^#{1,3}\s+(.+)$/);
    if (!heading) continue;
    for (const word of heading[1].split(/\s+/)) {
      const letters = word.match(/\p{L}{3,}/u);
      if (letters) return letters[0];
    }
  }
  return null;
}

describe('get-claude-md.js --section integration', { skip: SKIP_MSG }, () => {
  let tmpCacheDir;

  before(() => {
    tmpCacheDir = fs.mkdtempSync(path.join(os.tmpdir(), 'spovishun-section-test-'));
  });

  after(() => {
    if (tmpCacheDir && fs.existsSync(tmpCacheDir)) {
      fs.rmSync(tmpCacheDir, { recursive: true, force: true });
    }
  });

  test('--section returns only the matched section (not full content)', () => {
    // Warm the cache first with a full fetch
    const warmup = spawnSync('node', [path.join(SCRIPTS_DIR, 'get-claude-md.js')], {
      encoding: 'utf8',
      cwd: REPO_ROOT,
      env: { ...process.env, SPOVISHUN_CACHE_DIR: tmpCacheDir },
    });
    assert.equal(warmup.status, 0, `warmup stderr: ${warmup.stderr}`);

    // Derive a real section name from the live page instead of hard-coding one.
    // The CLAUDE.md Notion page is language-specific (currently Ukrainian), so a
    // fixed English heading like "Commands" is not guaranteed to exist. Match
    // is a case-insensitive substring on heading text (see lib/section-parser),
    // so any word (≥3 letters) from the first heading is a valid query.
    const query = pickSectionQuery(warmup.stdout);
    assert.ok(query, `no usable heading found in CLAUDE.md output:\n${warmup.stdout.slice(0, 200)}`);

    // Now query that section by name
    const result = spawnSync(
      'node',
      [path.join(SCRIPTS_DIR, 'get-claude-md.js'), '--section', query],
      {
        encoding: 'utf8',
        cwd: REPO_ROOT,
        env: { ...process.env, SPOVISHUN_CACHE_DIR: tmpCacheDir },
      }
    );

    assert.equal(result.status, 0, `stderr: ${result.stderr}`);
    assert.ok(result.stdout.length > 0, 'should return non-empty content');
    assert.ok(
      result.stdout.length < warmup.stdout.length,
      'section output should be shorter than full content'
    );
  });

  test('--section with unknown name exits 1 and lists available sections on stderr', () => {
    const result = spawnSync(
      'node',
      [path.join(SCRIPTS_DIR, 'get-claude-md.js'), '--section', 'nonexistent-xyz-abc'],
      {
        encoding: 'utf8',
        cwd: REPO_ROOT,
        env: { ...process.env, SPOVISHUN_CACHE_DIR: tmpCacheDir },
      }
    );

    assert.equal(result.status, 1);
    assert.match(result.stderr, /No section matching/);
    assert.match(result.stderr, /Available sections/);
  });
});
