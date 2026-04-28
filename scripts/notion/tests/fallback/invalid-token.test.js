'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('child_process');
const path = require('path');

const SCRIPTS_DIR = path.join(__dirname, '..', '..');
const BOGUS_TOKEN = 'bogus-invalid-token-xyz';

test('get-board exits with code 1 when token is invalid', () => {
  const result = spawnSync('node', [path.join(SCRIPTS_DIR, 'get-board.js')], {
    encoding: 'utf8',
    cwd: SCRIPTS_DIR,
    env: { NOTION_TOKEN: BOGUS_TOKEN },
  });

  assert.equal(result.status, 1, 'should exit with code 1 for invalid token');
});

test('get-board does not leak token value in error output', () => {
  const result = spawnSync('node', [path.join(SCRIPTS_DIR, 'get-board.js')], {
    encoding: 'utf8',
    cwd: SCRIPTS_DIR,
    env: { NOTION_TOKEN: BOGUS_TOKEN },
  });

  assert.ok(!result.stderr.includes(BOGUS_TOKEN), 'stderr must not contain the token value');
});

test('get-board does not print a stack trace on invalid token', () => {
  const result = spawnSync('node', [path.join(SCRIPTS_DIR, 'get-board.js')], {
    encoding: 'utf8',
    cwd: SCRIPTS_DIR,
    env: { NOTION_TOKEN: BOGUS_TOKEN },
  });

  assert.ok(!/at \w+ \(.+\.js:\d+/.test(result.stderr), 'stderr must not contain a stack trace');
});
