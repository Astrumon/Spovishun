#!/usr/bin/env node
/**
 * Stop hook — detects changes in architectural files and reminds to update Notion documentation.
 *
 * Runs after each Claude response (Stop event).
 * If trigger files are changed (DB tables, migrations, DI modules, commands, CLAUDE.md) —
 * outputs a systemMessage to the Claude Code UI.
 * If no triggers or git is unavailable — stays silent.
 * Always exits with exit(0) — errors do not block work.
 */

const { execSync } = require('child_process');

const TRIGGER_PATTERNS = [
  /data\/db\/table\/.*\.kt$/,
  /db\/migration\/V\d+.*\.sql$/,
  /di\/.*Module\.kt$/,
  /Application\.kt$/,
  /CLAUDE\.md$/,
  /commands\/.*Command\.kt$/,
  /build\.gradle\.kts$/,
];

function getChangedFiles() {
  try {
    const output = execSync('git diff --name-only HEAD', { encoding: 'utf8', timeout: 10000 });
    return output.trim().split('\n').filter(Boolean);
  } catch (err) {
    process.stderr.write(`[session-end] git unavailable: ${err.message}\n`);
    return [];
  }
}

function findTriggers(files) {
  return files.filter(f => TRIGGER_PATTERNS.some(pattern => pattern.test(f)));
}

try {
  const fs = require('fs');
  const path = require('path');

  const changedFiles = getChangedFiles();
  const triggered = findTriggers(changedFiles);

  if (triggered.length > 0) {
    const fileList = triggered.map(f => `  • ${f}`).join('\n');
    const message = `Doc-sync: architectural files changed:\n${fileList}\n\nRun doc-updater to sync Notion documentation.`;
    process.stdout.write(JSON.stringify({ systemMessage: message }) + '\n');
  }

  const stateFile = path.join(__dirname, '..', 'session-state.json');
  const state = {
    lastSession: new Date().toISOString(),
    changedFiles: changedFiles,
    docSyncNeeded: triggered.length > 0
  };
  fs.writeFileSync(stateFile, JSON.stringify(state, null, 2));
} catch (err) {
  process.stderr.write(`[session-end] Warning: ${err.message}\n`);
}

process.exit(0);