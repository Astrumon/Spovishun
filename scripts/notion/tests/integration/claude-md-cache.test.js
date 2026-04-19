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

describe('get-claude-md cache integration', { skip: SKIP_MSG }, () => {
  let tmpCacheDir;

  before(() => {
    tmpCacheDir = fs.mkdtempSync(path.join(os.tmpdir(), 'spovishun-cache-test-'));
  });

  after(() => {
    if (tmpCacheDir && fs.existsSync(tmpCacheDir)) {
      fs.rmSync(tmpCacheDir, { recursive: true, force: true });
    }
  });

  test('first call hits API and writes cache file', () => {
    const result = spawnSync('node', [path.join(SCRIPTS_DIR, 'get-claude-md.js')], {
      encoding: 'utf8',
      cwd: SCRIPTS_DIR,
      env: { ...process.env, SPOVISHUN_CACHE_DIR: tmpCacheDir },
    });

    assert.equal(result.status, 0, `stderr: ${result.stderr}`);
    assert.ok(result.stdout.length > 0, 'should return non-empty content');

    const cacheFile = path.join(tmpCacheDir, 'claude-md.json');
    assert.ok(fs.existsSync(cacheFile), 'cache file should exist after first call');
  });

  test('second call within TTL hits cache (mtime unchanged)', () => {
    const cacheFile = path.join(tmpCacheDir, 'claude-md.json');
    const mtimeBefore = fs.statSync(cacheFile).mtimeMs;

    const result = spawnSync('node', [path.join(SCRIPTS_DIR, 'get-claude-md.js')], {
      encoding: 'utf8',
      cwd: SCRIPTS_DIR,
      env: { ...process.env, SPOVISHUN_CACHE_DIR: tmpCacheDir },
    });

    assert.equal(result.status, 0, `stderr: ${result.stderr}`);
    const mtimeAfter = fs.statSync(cacheFile).mtimeMs;
    assert.equal(mtimeBefore, mtimeAfter, 'cache file mtime should not change on cache hit');
  });
});
