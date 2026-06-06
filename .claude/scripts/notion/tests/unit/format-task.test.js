'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { richText, extractBlocks } = require('../../lib/format-task');

// ─── richText ─────────────────────────────────────────────────────────────────

test('richText: joins plain_text from array', () => {
  assert.equal(richText([{ plain_text: 'Hello' }, { plain_text: ' World' }]), 'Hello World');
});

test('richText: trims whitespace', () => {
  assert.equal(richText([{ plain_text: '  trimmed  ' }]), 'trimmed');
});

test('richText: returns empty string for empty array', () => {
  assert.equal(richText([]), '');
});

test('richText: handles null/undefined gracefully', () => {
  assert.equal(richText(null), '');
  assert.equal(richText(undefined), '');
});

// ─── extractBlocks ────────────────────────────────────────────────────────────

test('extractBlocks: heading_1 → # text', () => {
  const blocks = [{ type: 'heading_1', heading_1: { rich_text: [{ plain_text: 'Top' }] } }];
  assert.match(extractBlocks(blocks), /^# Top/);
});

test('extractBlocks: heading_2 → ## text', () => {
  const blocks = [{ type: 'heading_2', heading_2: { rich_text: [{ plain_text: 'My Heading' }] } }];
  assert.match(extractBlocks(blocks), /^## My Heading/);
});

test('extractBlocks: heading_3 → ### text', () => {
  const blocks = [{ type: 'heading_3', heading_3: { rich_text: [{ plain_text: 'Sub' }] } }];
  assert.match(extractBlocks(blocks), /^### Sub/);
});

test('extractBlocks: paragraph → plain text', () => {
  const blocks = [{ type: 'paragraph', paragraph: { rich_text: [{ plain_text: 'Hello world' }] } }];
  assert.equal(extractBlocks(blocks), 'Hello world');
});

test('extractBlocks: bulleted_list_item → "- text"', () => {
  const blocks = [{ type: 'bulleted_list_item', bulleted_list_item: { rich_text: [{ plain_text: 'item' }] } }];
  assert.equal(extractBlocks(blocks), '- item');
});

test('extractBlocks: numbered_list_item → "- text"', () => {
  const blocks = [{ type: 'numbered_list_item', numbered_list_item: { rich_text: [{ plain_text: 'step' }] } }];
  assert.equal(extractBlocks(blocks), '- step');
});

test('extractBlocks: quote → "> text"', () => {
  const blocks = [{ type: 'quote', quote: { rich_text: [{ plain_text: 'note' }] } }];
  assert.equal(extractBlocks(blocks), '> note');
});

test('extractBlocks: skips blocks with empty text', () => {
  const blocks = [
    { type: 'paragraph', paragraph: { rich_text: [] } },
    { type: 'paragraph', paragraph: { rich_text: [{ plain_text: 'visible' }] } }
  ];
  assert.equal(extractBlocks(blocks), 'visible');
});

test('extractBlocks: skips blocks with missing content', () => {
  const blocks = [
    { type: 'divider', divider: null },
    { type: 'paragraph', paragraph: { rich_text: [{ plain_text: 'ok' }] } }
  ];
  assert.equal(extractBlocks(blocks), 'ok');
});
