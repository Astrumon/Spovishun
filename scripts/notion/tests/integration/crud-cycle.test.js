'use strict';

const { describe, test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('child_process');
const path = require('path');
const { loadToken } = require('../../lib/load-token');
const http = require('../../lib/notion-http');
const { toDashed } = require('../../lib/page-id');

const SCRIPTS_DIR = path.join(__dirname, '..', '..');
const token = loadToken();
const SKIP_MSG = token ? undefined : 'NOTION_TOKEN not set — skipping integration test';

describe('CRUD cycle integration', { skip: SKIP_MSG }, () => {
  let createdId = null;

  after(async () => {
    if (createdId && token) {
      await http.patch(token, `/v1/pages/${toDashed(createdId)}`, { archived: true });
    }
  });

  test('create-task: creates a task and returns id + url', () => {
    const input = JSON.stringify({ title: '[TEST-62] CRUD cycle', priority: 'Low', content: 'integration test' });
    const result = spawnSync('node', [path.join(SCRIPTS_DIR, 'create-task.js')], {
      input,
      encoding: 'utf8',
      cwd: SCRIPTS_DIR,
      env: process.env,
    });

    assert.equal(result.status, 0, `stderr: ${result.stderr}`);
    const out = JSON.parse(result.stdout);
    assert.ok(out.id, 'should return id');
    assert.ok(out.url, 'should return url');
    createdId = out.id;
  });

  test('get-task: retrieves created task by id', () => {
    assert.ok(createdId, 'requires create-task to have run first');
    const result = spawnSync('node', [path.join(SCRIPTS_DIR, 'get-task.js'), createdId], {
      encoding: 'utf8',
      cwd: SCRIPTS_DIR,
      env: process.env,
    });

    assert.equal(result.status, 0, `stderr: ${result.stderr}`);
    const task = JSON.parse(result.stdout);
    assert.ok(task.title.includes('[TEST-62]'), 'title should contain test prefix');
    assert.equal(task.status, 'To do');
    assert.equal(task.priority, 'Low');
    assert.ok(typeof task.content === 'string');
  });

  test('update-status: changes status to In progress', () => {
    assert.ok(createdId, 'requires create-task to have run first');
    const result = spawnSync('node', [path.join(SCRIPTS_DIR, 'update-status.js'), createdId, 'In progress'], {
      encoding: 'utf8',
      cwd: SCRIPTS_DIR,
      env: process.env,
    });

    assert.equal(result.status, 0, `stderr: ${result.stderr}`);
    const out = JSON.parse(result.stdout);
    assert.equal(out.status, 'In progress');
  });

  test('update-status: changes status to Done', () => {
    assert.ok(createdId, 'requires create-task to have run first');
    const result = spawnSync('node', [path.join(SCRIPTS_DIR, 'update-status.js'), createdId, 'Done'], {
      encoding: 'utf8',
      cwd: SCRIPTS_DIR,
      env: process.env,
    });

    assert.equal(result.status, 0, `stderr: ${result.stderr}`);
    const out = JSON.parse(result.stdout);
    assert.equal(out.status, 'Done');
  });
});
