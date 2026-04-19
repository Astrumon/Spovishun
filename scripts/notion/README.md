# scripts/notion — Shared Notion Library

Shared Node.js library for Notion operations across Claude Code tooling in the Spovishun project.

## Architecture

`lib/` is consumed by **two separate entry points**:

1. **`scripts/notion/*.js`** (CLI scripts — task 62): invoked manually or from CI.
2. **`.claude/hooks/notion-task-inject.js`** (hook — task 64): fires on every `UserPromptSubmit`.

Any breaking change to a `lib/` module requires testing **both** entry points.

### No SDK decision

`lib/` uses the built-in `https` module — NOT `@notionhq/client`.

**Why:** The hook fires on every prompt under a 30 s budget. The Notion SDK adds ~150–300 ms cold-start overhead on Windows (ESM + large module graph). Raw `https` keeps latency at ~50–100 ms. Since `lib/` is shared by the hook and scripts with no fork, both must use the same transport layer, and raw `https` wins.

## Setup

Copy `.env.example` to `.env` and add your token:

```
NOTION_SKILLS_TOKEN=ntn_...
```

Either `NOTION_SKILLS_TOKEN` or `NOTION_TOKEN` is accepted. Token lookup order:
1. `process.env.NOTION_SKILLS_TOKEN`
2. `process.env.NOTION_TOKEN`
3. `NOTION_TOKEN=` line in `.env` file (cwd)
4. `NOTION_SKILLS_TOKEN=` line in `.env` file (cwd)

## Modules

| Module | Exports | Ported from |
|---|---|---|
| `lib/constants.js` | `DATABASE_ID`, `NOTION_VERSION`, `CLAUDE_MD_PAGE_ID`, `ROOT_PAGE_ID`, `DOCS_ROOT_ID` | hook constants |
| `lib/load-token.js` | `loadToken()` | hook `loadToken()` |
| `lib/notion-http.js` | `request`, `get`, `post`, `patch` | hook `notionRequest()` |
| `lib/query-tasks.js` | `queryByPriorityTier(http, token, status, excludeIds)` | hook `queryByPriorityTier()` |
| `lib/extract-branch.js` | `extractTaskNumber`, `deriveBranchFromName`, `extractBranchFromBlocks` | hook branch utils |
| `lib/format-task.js` | `richText`, `extractBlocks` | hook `extractBlocks()` |
| `lib/page-id.js` | `toDashed`, `toCompact` | hook `applyPickMain` inline |
| `lib/cache.js` | `get`, `set`, `CACHE_DIR` | new (not in hook) |

## Scripts

_(To be added in task 62)_

## Testing

```bash
npm test
```

Uses built-in `node --test`. Zero external test dependencies. One test file per lib module.

## Zero runtime dependencies

`lib/` has **no** `dependencies` in `package.json`. Node built-ins only (`https`, `fs`, `path`, `os`, `child_process`).
