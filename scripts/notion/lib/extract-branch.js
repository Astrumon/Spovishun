'use strict';

const { richText } = require('./format-task');

function extractBranchFromBlocks(blocks) {
  for (let i = 0; i < blocks.length; i++) {
    const block = blocks[i];
    const type = block.type;
    if (!type) continue;
    const content = block[type];
    if (!content) continue;
    const text = richText(content.rich_text);
    if (text.includes('Branch name') || text.includes('🌿')) {
      for (let j = i + 1; j < blocks.length && j <= i + 3; j++) {
        const next = blocks[j];
        if (!next || !next.type) continue;
        const nextContent = next[next.type];
        if (!nextContent) continue;
        const nextText = richText(nextContent.rich_text);
        if (nextText && nextText.startsWith('feature/')) return nextText.trim();
      }
    }
    if (text.startsWith('feature/spovishun-')) return text.trim();
  }
  return null;
}

function extractTaskNumber(name) {
  const m = name.match(/spovishun-(\d+)/i);
  return m ? parseInt(m[1], 10) : null;
}

function deriveBranchFromName(name) {
  const numMatch = name.match(/spovishun-(\d+)/i);
  if (!numMatch) return null;
  const taskNum = numMatch[1];
  const slug = name
    .replace(/^feature\/spovishun-\d+:\s*/i, '')
    .toLowerCase()
    .replace(/[^a-z0-9\s-]/g, '')
    .trim()
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .split('-').slice(0, 3).join('-');
  return `feature/spovishun-${taskNum}-${slug}`;
}

module.exports = { richText, extractBranchFromBlocks, extractTaskNumber, deriveBranchFromName };
