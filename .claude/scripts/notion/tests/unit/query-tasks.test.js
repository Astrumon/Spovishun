'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { queryByPriorityTier } = require('../../lib/query-tasks');

function makePage(id, priority) {
  return {
    id,
    properties: {
      Priority: { select: { name: priority } }
    }
  };
}

function makeHttp(tierResponses, fallbackResults = []) {
  return {
    async post(token, path, body) {
      const tierFilter = body?.filter?.and?.[1]?.select?.equals;
      if (tierFilter && tierFilter in tierResponses) {
        return { object: 'list', results: tierResponses[tierFilter] };
      }
      return { object: 'list', results: fallbackResults };
    }
  };
}

test('returns High tier candidates when available', async () => {
  const http = makeHttp({ High: [makePage('aaa', 'High')], Medium: [], Low: [] });
  const result = await queryByPriorityTier(http, 'tok', 'To do', new Set());
  assert.equal(result.tier, 'High');
  assert.equal(result.candidates.length, 1);
  assert.equal(result.candidates[0].id, 'aaa');
});

test('cascades to Medium when High is empty', async () => {
  const http = makeHttp({ High: [], Medium: [makePage('bbb', 'Medium')], Low: [] });
  const result = await queryByPriorityTier(http, 'tok', 'To do', new Set());
  assert.equal(result.tier, 'Medium');
  assert.equal(result.candidates[0].id, 'bbb');
});

test('cascades to fallback when all tiers empty', async () => {
  const fallbackPage = makePage('fallback-id', 'Normal');
  const http = makeHttp({ High: [], Medium: [], Low: [] }, [fallbackPage]);
  const result = await queryByPriorityTier(http, 'tok', 'To do', new Set());
  assert.equal(result.tier, null);
  assert.equal(result.candidates[0].id, 'fallback-id');
});

test('excludes pages whose compact IDs are in excludePageIds', async () => {
  const compactId = 'aaaabbbbccccddddeeeeffffaaaabbbb';
  const dashedId = 'aaaabbbb-cccc-dddd-eeee-ffffaaaabbbb';
  const http = makeHttp({ High: [makePage(dashedId, 'High')] });
  const result = await queryByPriorityTier(http, 'tok', 'To do', new Set([compactId]));
  assert.equal(result.candidates.length, 0);
});

test('skips tiers that return API errors', async () => {
  const http = {
    async post(token, path, body) {
      const tier = body?.filter?.and?.[1]?.select?.equals;
      if (tier === 'High') return { object: 'error', message: 'unauthorized' };
      if (tier === 'Medium') return { object: 'list', results: [makePage('zzz', 'Medium')] };
      return { object: 'list', results: [] };
    }
  };
  const result = await queryByPriorityTier(http, 'tok', 'To do', new Set());
  assert.equal(result.tier, 'Medium');
});
