'use strict';

const { test, beforeEach, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const os = require('os');
const fs = require('fs');
const path = require('path');
const { loadToken } = require('../../lib/load-token');

let savedCwd;
let savedSkillsToken;
let savedToken;

beforeEach(() => {
  savedCwd = process.cwd();
  savedSkillsToken = process.env.NOTION_SKILLS_TOKEN;
  savedToken = process.env.NOTION_TOKEN;
  delete process.env.NOTION_SKILLS_TOKEN;
  delete process.env.NOTION_TOKEN;
});

afterEach(() => {
  process.chdir(savedCwd);
  if (savedSkillsToken !== undefined) process.env.NOTION_SKILLS_TOKEN = savedSkillsToken;
  else delete process.env.NOTION_SKILLS_TOKEN;
  if (savedToken !== undefined) process.env.NOTION_TOKEN = savedToken;
  else delete process.env.NOTION_TOKEN;
});

// NOTION_TOKEN wins on the env path exactly as it does on the .env path below and
// in hooks/hook-config.js — one precedence everywhere, so a stale NOTION_SKILLS_TOKEN
// cannot silently shadow a working NOTION_TOKEN.
test('returns NOTION_TOKEN first when both env vars set', () => {
  process.env.NOTION_SKILLS_TOKEN = 'skills-tok';
  process.env.NOTION_TOKEN = 'regular-tok';
  assert.equal(loadToken(), 'regular-tok');
});

test('falls back to NOTION_TOKEN when NOTION_SKILLS_TOKEN absent', () => {
  process.env.NOTION_TOKEN = 'regular-tok';
  assert.equal(loadToken(), 'regular-tok');
});

test('reads NOTION_TOKEN from .env file when both env vars absent', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'notion-test-'));
  fs.writeFileSync(path.join(tmpDir, '.env'), 'NOTION_TOKEN=from-file\n', 'utf8');
  process.chdir(tmpDir);
  try {
    assert.equal(loadToken(), 'from-file');
  } finally {
    process.chdir(savedCwd);
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('.env: NOTION_TOKEN takes precedence over NOTION_SKILLS_TOKEN in file', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'notion-test-'));
  fs.writeFileSync(path.join(tmpDir, '.env'), 'NOTION_SKILLS_TOKEN=skills\nNOTION_TOKEN=primary\n', 'utf8');
  process.chdir(tmpDir);
  try {
    assert.equal(loadToken(), 'primary');
  } finally {
    process.chdir(savedCwd);
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('reads NOTION_SKILLS_TOKEN from .env when NOTION_TOKEN absent in file', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'notion-test-'));
  fs.writeFileSync(path.join(tmpDir, '.env'), 'NOTION_SKILLS_TOKEN=skills-file\n', 'utf8');
  process.chdir(tmpDir);
  try {
    assert.equal(loadToken(), 'skills-file');
  } finally {
    process.chdir(savedCwd);
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('returns null when no token found anywhere', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'notion-test-'));
  process.chdir(tmpDir);
  try {
    assert.equal(loadToken(), null);
  } finally {
    process.chdir(savedCwd);
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});
