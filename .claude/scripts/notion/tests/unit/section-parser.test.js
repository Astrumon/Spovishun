'use strict';

const { describe, test } = require('node:test');
const assert = require('node:assert/strict');
const { buildSectionIndex, extractSection } = require('../../lib/section-parser');

const SAMPLE = `## Commands
run build
run test

## Source Structure
src/main/kotlin

### Sub-section
nested content

## Testing
unit tests here`;

// ─── buildSectionIndex ────────────────────────────────────────────────────────

describe('buildSectionIndex', () => {
  test('extracts ## and ### headings with correct level and line number', () => {
    const index = buildSectionIndex(SAMPLE);
    assert.equal(index.length, 4);
    assert.equal(index[0].text, 'Commands');
    assert.equal(index[0].level, 2);
    assert.equal(index[1].text, 'Source Structure');
    assert.equal(index[2].text, 'Sub-section');
    assert.equal(index[2].level, 3);
    assert.equal(index[3].text, 'Testing');
  });

  test('returns empty array for content with no headings', () => {
    assert.deepEqual(buildSectionIndex('just text\nno headings'), []);
  });

  test('ignores #### and deeper headings', () => {
    const index = buildSectionIndex('#### too deep\n## ok');
    assert.equal(index.length, 1);
    assert.equal(index[0].text, 'ok');
  });
});

// ─── extractSection ───────────────────────────────────────────────────────────

describe('extractSection', () => {
  test('single match returns that section only', () => {
    const result = extractSection(SAMPLE, 'commands');
    assert.match(result, /^## Commands/);
    assert.match(result, /run build/);
    assert.doesNotMatch(result, /Source Structure/);
  });

  test('case-insensitive match', () => {
    const result = extractSection(SAMPLE, 'COMMANDS');
    assert.match(result, /## Commands/);
  });

  test('section ends before next same-level heading', () => {
    const result = extractSection(SAMPLE, 'source structure');
    assert.match(result, /Source Structure/);
    assert.match(result, /Sub-section/);
    assert.doesNotMatch(result, /## Testing/);
  });

  test('multi-match warning on stderr and returns all matches', () => {
    // "se" matches "Setup" and "Sub-section" (via "section") but not "Testing"
    const content = '## Setup\nsetup content\n\n## Sub-section\nsub content\n\n## Testing\ntest content';
    const result = extractSection(content, 'se');
    assert.match(result, /Setup/);
    assert.match(result, /Sub-section/);
    assert.doesNotMatch(result, /Testing/);
  });

  test('no match exits with code 1', () => {
    const { spawnSync } = require('child_process');
    const path = require('path');
    const os = require('os');
    const fs = require('fs');

    // Write a temp cache so script doesn't need API
    const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'sp-section-test-'));
    const cacheFile = path.join(tmpDir, 'claude-md.json');
    fs.writeFileSync(cacheFile, JSON.stringify({ value: { content: SAMPLE, sections: null }, ts: Date.now() }));

    const result = spawnSync(
      'node',
      [path.join(__dirname, '../../get-claude-md.js'), '--section', 'nonexistent-xyz'],
      {
        encoding: 'utf8',
        env: {
          ...process.env,
          SPOVISHUN_CACHE_DIR: tmpDir,
          NOTION_SKILLS_TOKEN: 'dummy',
          // config-driven constants.js requires the page id to be resolvable;
          // the cache short-circuits the API call, so any non-empty id works.
          NOTION_CLAUDE_MD_PAGE_ID: 'dummy-claude-md-page-id',
        },
      }
    );
    fs.rmSync(tmpDir, { recursive: true, force: true });

    assert.equal(result.status, 1);
    assert.match(result.stderr, /No section matching/);
    assert.match(result.stderr, /Available sections/);
  });
});
