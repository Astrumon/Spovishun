#!/usr/bin/env node
/**
 * SessionStart hook — reads session-state.json and reminds about pending doc sync.
 *
 * If docSyncNeeded is true from the previous session — outputs a systemMessage.
 * If session-state.json does not exist — exits silently.
 * Always exits with exit(0) — errors do not block session start.
 */

const fs = require('fs');
const path = require('path');

const stateFile = path.join(__dirname, '..', 'session-state.json');

try {
  if (!fs.existsSync(stateFile)) process.exit(0);
  const state = JSON.parse(fs.readFileSync(stateFile, 'utf8'));
  if (state.docSyncNeeded) {
    process.stdout.write(JSON.stringify({
      systemMessage: 'Last session left doc sync pending. Run doc-updater if still relevant.'
    }) + '\n');
  }
} catch (err) {
  // Silent — don't block session start
}

process.exit(0);
