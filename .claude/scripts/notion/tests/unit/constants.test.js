'use strict';

const { test, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const constants = require('../../lib/constants');

// constants.js is config-driven: each ID getter resolves from its env var
// first, then from spovishun-skills.config.yaml, falling back to ''. These
// tests pin that contract, not the consumer's specific UUID values (those
// live in config and would make the test project-specific).

const ID_GETTERS = [
  ['DATABASE_ID', 'NOTION_DATABASE_ID'],
  ['EPICS_DATABASE_ID', 'NOTION_EPICS_DATABASE_ID'],
  ['EPICS_GROUP_PAGE_ID', 'NOTION_EPICS_GROUP_PAGE_ID'],
  ['CLAUDE_MD_PAGE_ID', 'NOTION_CLAUDE_MD_PAGE_ID'],
  ['ROOT_PAGE_ID', 'NOTION_ROOT_PAGE_ID'],
  ['DOCS_ROOT_ID', 'NOTION_DOCS_ROOT_ID'],
];

const savedEnv = {};
afterEach(() => {
  for (const [, envName] of ID_GETTERS) {
    if (envName in savedEnv) {
      process.env[envName] = savedEnv[envName];
      delete savedEnv[envName];
    } else {
      delete process.env[envName];
    }
  }
});

function overrideEnv(name, value) {
  savedEnv[name] = process.env[name];
  process.env[name] = value;
}

for (const [key, envName] of ID_GETTERS) {
  test(`${key} resolves from ${envName} env var (env wins over config)`, () => {
    overrideEnv(envName, 'env-override-value');
    assert.equal(constants[key], 'env-override-value');
  });
}

test('NOTION_VERSION has expected value', () => {
  assert.equal(constants.NOTION_VERSION, '2022-06-28');
});

test('every ID getter returns a string (never undefined/null)', () => {
  for (const [key] of ID_GETTERS) {
    assert.equal(typeof constants[key], 'string', `${key} should be a string`);
  }
});

test('all expected keys are exported', () => {
  const keys = [...ID_GETTERS.map(([k]) => k), 'NOTION_VERSION'];
  for (const key of keys) {
    assert.ok(key in constants, `missing export: ${key}`);
  }
});
