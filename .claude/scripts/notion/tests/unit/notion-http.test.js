'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const https = require('https');
const notionHttp = require('../../lib/notion-http');
const { NOTION_VERSION } = require('../../lib/constants');

function makeFakeHttps(responseBody) {
  return function fakeRequest(options, callback) {
    const res = {
      on(event, handler) {
        if (event === 'data') handler(responseBody);
        if (event === 'end') handler();
        return res;
      }
    };
    callback(res);
    return { on() {}, end() {}, write() {} };
  };
}

test('get() sends correct hostname and headers', async (t) => {
  let captured;
  t.mock.method(https, 'request', (options, callback) => {
    captured = options;
    return makeFakeHttps('{"object":"page"}')(options, callback);
  });

  await notionHttp.get('my-token', '/v1/pages/abc');

  assert.equal(captured.hostname, 'api.notion.com');
  assert.equal(captured.method, 'GET');
  assert.equal(captured.path, '/v1/pages/abc');
  assert.equal(captured.headers['Authorization'], 'Bearer my-token');
  assert.equal(captured.headers['Notion-Version'], NOTION_VERSION);
});

test('post() sets method to POST and sends body', async (t) => {
  let captured;
  let writtenBody;

  t.mock.method(https, 'request', (options, callback) => {
    captured = options;
    const req = {
      on() {},
      write(data) { writtenBody = data; },
      end() {}
    };
    const res = {
      on(ev, h) {
        if (ev === 'data') h('{}');
        if (ev === 'end') h();
        return res;
      }
    };
    callback(res);
    return req;
  });

  await notionHttp.post('tok', '/v1/databases/abc/query', { filter: {} });

  assert.equal(captured.method, 'POST');
  assert.ok(captured.headers['Content-Length'] > 0);
  assert.ok(writtenBody.includes('"filter"'));
});

test('patch() sets method to PATCH', async (t) => {
  let captured;
  t.mock.method(https, 'request', (options, callback) => {
    captured = options;
    return makeFakeHttps('{}')(options, callback);
  });

  await notionHttp.patch('tok', '/v1/pages/abc', { properties: {} });

  assert.equal(captured.method, 'PATCH');
});

test('resolves null on malformed JSON response', async (t) => {
  t.mock.method(https, 'request', makeFakeHttps('not-valid-json{{{'));

  const result = await notionHttp.get('tok', '/v1/test');
  assert.equal(result, null);
});

test('rejects on network error', async (t) => {
  t.mock.method(https, 'request', (options, callback) => {
    const req = {
      on(event, handler) {
        if (event === 'error') handler(new Error('ECONNREFUSED'));
        return req;
      },
      end() {},
      write() {}
    };
    return req;
  });

  await assert.rejects(() => notionHttp.get('tok', '/v1/fail'), /ECONNREFUSED/);
});
