'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { extractTaskNumber, deriveBranchFromName, extractBranchFromBlocks } = require('../../lib/extract-branch');

// ─── extractTaskNumber ────────────────────────────────────────────────────────

test('extractTaskNumber: parses number from feature branch format', () => {
  assert.equal(extractTaskNumber('feature/spovishun-42: My Task'), 42);
});

test('extractTaskNumber: parses from plain task name', () => {
  assert.equal(extractTaskNumber('spovishun-7 fix bug'), 7);
});

test('extractTaskNumber: returns null for non-matching string', () => {
  assert.equal(extractTaskNumber('garbage text'), null);
});

test('extractTaskNumber: case-insensitive', () => {
  assert.equal(extractTaskNumber('SPOVISHUN-99'), 99);
});

// ─── deriveBranchFromName ─────────────────────────────────────────────────────

test('deriveBranchFromName: produces correct branch slug', () => {
  const result = deriveBranchFromName('feature/spovishun-61: Notion tooling foundation');
  assert.equal(result, 'feature/spovishun-61-notion-tooling-foundation');
});

test('deriveBranchFromName: slug is max 3 words', () => {
  const result = deriveBranchFromName('feature/spovishun-10: one two three four five');
  assert.equal(result, 'feature/spovishun-10-one-two-three');
});

test('deriveBranchFromName: strips special chars from slug', () => {
  const result = deriveBranchFromName('feature/spovishun-5: Fix! Auth & Tokens');
  assert.match(result, /^feature\/spovishun-5-fix-auth-tokens$/);
});

test('deriveBranchFromName: returns null when no task number', () => {
  assert.equal(deriveBranchFromName('random task name'), null);
});

// ─── extractBranchFromBlocks ──────────────────────────────────────────────────

test('extractBranchFromBlocks: finds branch after Branch name heading', () => {
  const blocks = [
    { type: 'heading_2', heading_2: { rich_text: [{ plain_text: '🌿 Branch name' }] } },
    { type: 'paragraph', paragraph: { rich_text: [{ plain_text: 'feature/spovishun-61-notion-lib-foundation' }] } }
  ];
  assert.equal(extractBranchFromBlocks(blocks), 'feature/spovishun-61-notion-lib-foundation');
});

test('extractBranchFromBlocks: finds inline feature/ branch in paragraph', () => {
  const blocks = [
    { type: 'paragraph', paragraph: { rich_text: [{ plain_text: 'feature/spovishun-55-admin-preregistration' }] } }
  ];
  assert.equal(extractBranchFromBlocks(blocks), 'feature/spovishun-55-admin-preregistration');
});

test('extractBranchFromBlocks: returns null when no branch found', () => {
  const blocks = [
    { type: 'paragraph', paragraph: { rich_text: [{ plain_text: 'some text without branch' }] } }
  ];
  assert.equal(extractBranchFromBlocks(blocks), null);
});

test('extractBranchFromBlocks: skips blocks with missing content', () => {
  const blocks = [
    { type: 'divider', divider: {} },
    { type: 'paragraph', paragraph: { rich_text: [{ plain_text: 'feature/spovishun-1-test' }] } }
  ];
  assert.equal(extractBranchFromBlocks(blocks), 'feature/spovishun-1-test');
});
