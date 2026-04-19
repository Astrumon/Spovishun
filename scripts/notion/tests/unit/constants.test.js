'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const constants = require('../../lib/constants');

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const COMPACT_RE = /^[0-9a-f]{32}$/i;

test('DATABASE_ID is a valid compact UUID (32 hex chars)', () => {
  assert.match(constants.DATABASE_ID, COMPACT_RE);
});

test('NOTION_VERSION has expected value', () => {
  assert.equal(constants.NOTION_VERSION, '2022-06-28');
});

test('CLAUDE_MD_PAGE_ID is a valid dashed UUID', () => {
  assert.match(constants.CLAUDE_MD_PAGE_ID, UUID_RE);
});

test('ROOT_PAGE_ID is a valid dashed UUID', () => {
  assert.match(constants.ROOT_PAGE_ID, UUID_RE);
});

test('DOCS_ROOT_ID is a valid compact UUID (32 hex chars)', () => {
  assert.match(constants.DOCS_ROOT_ID, COMPACT_RE);
});

test('all expected keys are exported', () => {
  const keys = ['DATABASE_ID', 'NOTION_VERSION', 'CLAUDE_MD_PAGE_ID', 'ROOT_PAGE_ID', 'DOCS_ROOT_ID'];
  for (const key of keys) {
    assert.ok(key in constants, `missing export: ${key}`);
  }
});
