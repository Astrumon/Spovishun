'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { toDashed, toCompact } = require('../../lib/page-id');

const COMPACT = '3193462f68a980d69ec9c7ccc6329b88';
const DASHED  = '3193462f-68a9-80d6-9ec9-c7ccc6329b88';

test('toDashed: converts 32-char compact to dashed UUID', () => {
  const result = toDashed(COMPACT);
  assert.match(result, /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
});

test('toDashed: is idempotent (already dashed → unchanged)', () => {
  const dashed = toDashed(COMPACT);
  assert.equal(toDashed(dashed), dashed);
});

test('toCompact: strips dashes from dashed UUID', () => {
  const dashed = toDashed(COMPACT);
  assert.equal(toCompact(dashed), COMPACT);
});

test('toCompact: is idempotent (already compact → unchanged)', () => {
  assert.equal(toCompact(COMPACT), COMPACT);
});

test('round-trip: toCompact(toDashed(x)) === x', () => {
  assert.equal(toCompact(toDashed(COMPACT)), COMPACT);
});

test('toDashed: returns input as-is for invalid length', () => {
  assert.equal(toDashed('short'), 'short');
  assert.equal(toDashed(''), '');
});

test('toDashed: returns input as-is for null/undefined', () => {
  assert.equal(toDashed(null), null);
  assert.equal(toDashed(undefined), undefined);
});

test('toCompact: handles null/undefined gracefully', () => {
  assert.equal(toCompact(null), null);
  assert.equal(toCompact(undefined), undefined);
});
