'use strict';

const { describe, test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('child_process');
const os = require('os');
const fs = require('fs');
const path = require('path');
const { loadToken } = require('../../lib/load-token');

const SCRIPTS_DIR = path.join(__dirname, '..', '..');
const token = loadToken();
const SKIP_MSG = token ? undefined : 'NOTION_TOKEN not set — skipping integration test';

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
      cwd: SCRIPTS_DIR,
      env: { ...process.env, SPOVISHUN_CACHE_DIR: tmpCacheDir },
    });
    assert.equal(warmup.status, 0, `warmup stderr: ${warmup.stderr}`);

    // Now query a known section from CLAUDE.md ("Commands" is always present)
    const result = spawnSync(
      'node',
      [path.join(SCRIPTS_DIR, 'get-claude-md.js'), '--section', 'commands'],
      {
        encoding: 'utf8',
        cwd: SCRIPTS_DIR,
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
        cwd: SCRIPTS_DIR,
        env: { ...process.env, SPOVISHUN_CACHE_DIR: tmpCacheDir },
      }
    );

    assert.equal(result.status, 1);
    assert.match(result.stderr, /No section matching/);
    assert.match(result.stderr, /Available sections/);
  });
});
