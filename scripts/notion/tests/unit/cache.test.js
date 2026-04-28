'use strict';

const { test, beforeEach, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const os = require('os');
const fs = require('fs');
const path = require('path');
const cache = require('../../lib/cache');

let tmpDir;

beforeEach(() => {
  tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'cache-test-'));
});

afterEach(() => {
  fs.rmSync(tmpDir, { recursive: true, force: true });
});

test('set and get: returns stored value within TTL', () => {
  cache.set('key1', { hello: 'world' }, tmpDir);
  const val = cache.get('key1', 60_000, tmpDir);
  assert.deepEqual(val, { hello: 'world' });
});

test('get: returns null when TTL expired', () => {
  cache.set('key2', 'data', tmpDir);
  const val = cache.get('key2', 0, tmpDir);
  assert.equal(val, null);
});

test('get: returns null on cache miss (key does not exist)', () => {
  const val = cache.get('nonexistent', 60_000, tmpDir);
  assert.equal(val, null);
});

test('get: returns null on corrupted cache file', () => {
  fs.writeFileSync(path.join(tmpDir, 'bad.json'), 'not-json{{{', 'utf8');
  const val = cache.get('bad', 60_000, tmpDir);
  assert.equal(val, null);
});

test('set: auto-creates cache directory if missing', () => {
  const newDir = path.join(tmpDir, 'nested', 'cache');
  assert.ok(!fs.existsSync(newDir));
  cache.set('k', 'v', newDir);
  assert.ok(fs.existsSync(newDir));
  assert.equal(cache.get('k', 60_000, newDir), 'v');
});

test('set: overwrites existing cache entry', () => {
  cache.set('dup', 'first', tmpDir);
  cache.set('dup', 'second', tmpDir);
  assert.equal(cache.get('dup', 60_000, tmpDir), 'second');
});

test('exports CACHE_DIR as a string path', () => {
  assert.equal(typeof cache.CACHE_DIR, 'string');
  assert.ok(cache.CACHE_DIR.length > 0);
});
