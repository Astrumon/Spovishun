'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('child_process');
const path = require('path');

const SCRIPTS_DIR = path.join(__dirname, '..', '..');

test('get-board exits with code 2 when no token env vars are set', () => {
  const result = spawnSync('node', [path.join(SCRIPTS_DIR, 'get-board.js')], {
    encoding: 'utf8',
    cwd: SCRIPTS_DIR,
    env: {},
  });

  assert.equal(result.status, 2, 'should exit with code 2 for missing token');
  assert.ok(result.stderr.length > 0, 'should write error message to stderr');
  assert.equal(result.stdout.trim(), '', 'stdout should be empty');
});
