'use strict';

const { describe, test } = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('child_process');
const path = require('path');
const { loadToken } = require('../../lib/load-token');

const SCRIPTS_DIR = path.join(__dirname, '..', '..');
const token = loadToken();
const SKIP_MSG = token ? undefined : 'NOTION_TOKEN not set — skipping integration test';

describe('get-board integration', { skip: SKIP_MSG }, () => {
  test('returns array with expected shape', () => {
    const result = spawnSync('node', [path.join(SCRIPTS_DIR, 'get-board.js')], {
      encoding: 'utf8',
      cwd: SCRIPTS_DIR,
      env: process.env,
    });

    assert.equal(result.status, 0, `stderr: ${result.stderr}`);
    const tasks = JSON.parse(result.stdout);
    assert.ok(Array.isArray(tasks), 'output should be an array');
    if (tasks.length > 0) {
      const task = tasks[0];
      assert.ok('id' in task, 'task should have id');
      assert.ok('title' in task, 'task should have title');
      assert.ok('status' in task, 'task should have status');
      assert.ok('branch' in task, 'task should have branch');
      assert.ok('priority' in task, 'task should have priority');
    }
  });

  test('--priority-tier returns tasks sorted by tier', () => {
    const result = spawnSync('node', [path.join(SCRIPTS_DIR, 'get-board.js'), '--priority-tier'], {
      encoding: 'utf8',
      cwd: SCRIPTS_DIR,
      env: process.env,
    });

    assert.equal(result.status, 0, `stderr: ${result.stderr}`);
    const tasks = JSON.parse(result.stdout);
    assert.ok(Array.isArray(tasks));

    const TIER_ORDER = ['High', 'Medium', 'Low'];
    let lastTierIndex = -1;
    for (const task of tasks) {
      if (task.priority === null) continue;
      const idx = TIER_ORDER.indexOf(task.priority);
      if (idx === -1) continue;
      assert.ok(idx >= lastTierIndex, `priority "${task.priority}" appeared after a lower-priority task`);
      lastTierIndex = idx;
    }
  });
});
