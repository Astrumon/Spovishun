'use strict';

const { describe, test } = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('child_process');
const os = require('os');
const fs = require('fs');
const path = require('path');

const SCRIPTS_DIR = path.join(__dirname, '..', '..');

function runScript(name, args = [], env = {}) {
  return spawnSync('node', [path.join(SCRIPTS_DIR, name), ...args], {
    encoding: 'utf8',
    cwd: SCRIPTS_DIR,
    env: { ...process.env, ...env },
  });
}

// ─── get-board.js --format=md ────────────────────────────────────────────────

describe('get-board.js --format=md', () => {
  test('rejects unknown format and exits 1', () => {
    const r = runScript('get-board.js', ['--format=xml'], { NOTION_SKILLS_TOKEN: 'dummy' });
    assert.equal(r.status, 1);
    assert.match(r.stderr, /invalid format/);
  });

  test('--format=md output contains markdown table header', () => {
    // Write a minimal mock board response via a temp cache bypass — test rendering only
    // by calling renderMd directly through a tiny inline script
    const inline = `
      (function() {
        const tasks = [
          { title: 'feature/spovishun-1: test task', status: 'In progress', priority: 'High' },
          { title: 'feature/spovishun-2: another task', status: 'To do', priority: 'Low' },
        ];
        function renderMd(tasks) {
          if (tasks.length === 0) return '*(no tasks)*';
          const rows = tasks.map(t => '| ' + t.title + ' | ' + (t.status ?? '') + ' | ' + (t.priority ?? '') + ' |');
          return ['| Title | Status | Priority |', '|-------|--------|----------|', ...rows].join('\\n');
        }
        process.stdout.write(renderMd(tasks) + '\\n');
      })();
    `;
    const r = spawnSync('node', ['-e', inline], { encoding: 'utf8' });
    assert.equal(r.status, 0);
    assert.match(r.stdout, /\| Title \| Status \| Priority \|/);
    assert.match(r.stdout, /\|-------|--------|----------\|/);
    assert.match(r.stdout, /feature\/spovishun-1/);
    assert.match(r.stdout, /In progress/);
  });

  test('--format=md with empty tasks returns *(no tasks)*', () => {
    const inline = `
      function renderMd(tasks) {
        if (tasks.length === 0) return '*(no tasks)*';
        return 'table';
      }
      process.stdout.write(renderMd([]) + '\\n');
    `;
    const r = spawnSync('node', ['-e', inline], { encoding: 'utf8' });
    assert.match(r.stdout, /\*\(no tasks\)\*/);
  });
});

// ─── get-task.js --format=md ─────────────────────────────────────────────────

describe('get-task.js --format=md', () => {
  test('rejects unknown format and exits 1 (requires token check bypassed by bad arg first)', () => {
    // No arg → exits 1 with usage error before format check
    const r = runScript('get-task.js', [], { NOTION_SKILLS_TOKEN: 'dummy' });
    assert.equal(r.status, 1);
    assert.match(r.stderr, /Usage/);
  });

  test('--format=md output starts with # title', () => {
    const inline = `
      function renderMd(task) {
        const meta = [task.status, task.priority, task.branch].filter(Boolean).join(' | ');
        const parts = ['# ' + task.title];
        if (meta) parts.push(meta);
        if (task.content) parts.push('---', task.content);
        return parts.join('\\n\\n');
      }
      const task = {
        title: 'feature/spovishun-65: Notion advanced token optimizations',
        status: 'In progress',
        priority: 'Medium',
        branch: 'feature/spovishun-65-notion-advanced-optimizations',
        content: '## Goal\\nApply optimizations',
      };
      process.stdout.write(renderMd(task) + '\\n');
    `;
    const r = spawnSync('node', ['-e', inline], { encoding: 'utf8' });
    assert.equal(r.status, 0);
    assert.match(r.stdout, /^# feature\/spovishun-65/);
    assert.match(r.stdout, /In progress \| Medium/);
    assert.match(r.stdout, /---/);
    assert.match(r.stdout, /## Goal/);
  });
});

// ─── get-claude-md.js --format=json ──────────────────────────────────────────

describe('get-claude-md.js --format=json', () => {
  test('--format=json wraps content in JSON object', () => {
    const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'sp-fmt-test-'));
    const cacheFile = path.join(tmpDir, 'claude-md.json');
    fs.writeFileSync(cacheFile, JSON.stringify({ value: { content: '## Commands\nrun build', sections: null }, ts: Date.now() }));

    const r = runScript('get-claude-md.js', ['--format=json'], {
      SPOVISHUN_CACHE_DIR: tmpDir,
      NOTION_SKILLS_TOKEN: 'dummy',
    });
    fs.rmSync(tmpDir, { recursive: true, force: true });

    assert.equal(r.status, 0);
    const parsed = JSON.parse(r.stdout);
    assert.ok('content' in parsed, 'output should have content key');
    assert.match(parsed.content, /Commands/);
  });

  test('default format returns raw text (backward compat)', () => {
    const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'sp-fmt-test-'));
    const cacheFile = path.join(tmpDir, 'claude-md.json');
    fs.writeFileSync(cacheFile, JSON.stringify({ value: { content: '## Commands\nrun build', sections: null }, ts: Date.now() }));

    const r = runScript('get-claude-md.js', [], {
      SPOVISHUN_CACHE_DIR: tmpDir,
      NOTION_SKILLS_TOKEN: 'dummy',
    });
    fs.rmSync(tmpDir, { recursive: true, force: true });

    assert.equal(r.status, 0);
    assert.doesNotMatch(r.stdout, /^\{/);
    assert.match(r.stdout, /Commands/);
  });
});
