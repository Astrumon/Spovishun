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

All scripts follow the same contract:

| Exit code | Meaning |
|---|---|
| 0 | Success |
| 1 | Validation error, missing argument, invalid stdin JSON, Notion API error |
| 2 | `NOTION_TOKEN` / `NOTION_SKILLS_TOKEN` not set |

stdout = result JSON (or plain text for `get-claude-md.js`). stderr = human-readable errors. No banners, no progress output.

### `get-board.js`

List tasks from the board.

```bash
node get-board.js [--status <name>] [--priority-tier] [--format <fmt>]
npm run board
npm run board -- --priority-tier
npm run board -- --status "In progress"
npm run board -- --format md       # markdown table (human-readable)
```

Options:
- `--status <name>` — filter by status (default: `To do`). Valid values: `Not started`, `To do`, `In progress`, `Done`.
- `--priority-tier` — use the High→Medium→Low cascade from `lib/query-tasks.js` (up to 5 per tier).
- `--format <fmt>` — output format: `json` (default), `md` (markdown table), `text` (plain list).

Sample output (`--format json`, default):
```json
[
  {
    "id": "3453462f-68a9-811e-bbd9-c6d7b7847c67",
    "title": "feature/spovishun-62: Notion CLI scripts + integration tests",
    "status": "In progress",
    "branch": "feature/spovishun-62-notion-cli",
    "priority": "High"
  }
]
```

Sample output (`--format md`):
```
| # | Title | Status | Priority |
|---|---|---|---|
| 62 | Notion CLI scripts + integration tests | In progress | High |
```

### `get-task.js`

Fetch a single task by page ID or task number.

```bash
node get-task.js <pageId | spovishun-N> [--format <fmt>]
npm run task -- spovishun-62
npm run task -- 3453462f68a9811ebbd9c6d7b7847c67
npm run task -- 62 --format text   # human-readable summary
```

Options:
- `--format <fmt>` — output format: `json` (default), `md` (markdown card), `text` (plain summary).

Sample output (`--format json`, default):
```json
{
  "id": "3453462f-68a9-811e-bbd9-c6d7b7847c67",
  "title": "feature/spovishun-62: Notion CLI scripts + integration tests",
  "status": "In progress",
  "branch": "feature/spovishun-62-notion-cli-scripts",
  "priority": "High",
  "content": "**Goal**\nBuild 5 CLI scripts..."
}
```

### `create-task.js`

Create a new task by reading JSON from stdin.

```bash
echo '{"title":"My task","priority":"High","content":"Details here","icon":"🔨"}' | node create-task.js
```

Stdin schema:
```json
{
  "title": "string (required)",
  "priority": "High | Medium | Low (required)",
  "content": "string (optional)",
  "icon": "single emoji string (optional)"
}
```

Sample output:
```json
{
  "id": "abc123...",
  "url": "https://www.notion.so/abc123..."
}
```

### `update-status.js`

Change the Status property of a task.

```bash
node update-status.js <task-id> <new-status>
node update-status.js 3453462f68a9811ebbd9c6d7b7847c67 "Done"
```

Valid statuses: `Not started`, `To do`, `In progress`, `Done`.

Sample output:
```json
{
  "id": "3453462f-68a9-811e-bbd9-c6d7b7847c67",
  "status": "Done"
}
```

### `get-claude-md.js`

Fetch the CLAUDE.md page content with a 1-hour file cache.

```bash
node get-claude-md.js [--section <query>] [--format <fmt>]
npm run claude-md
npm run claude-md -- --section commands        # extract just the Commands section
npm run claude-md -- --section testing         # extract just the Testing section
npm run claude-md -- --section architecture    # extract Source Structure + Layer Rules
```

Options:
- `--section <query>` — case-insensitive substring match against heading names. Returns the matched section only. If multiple headings match, all are returned with a warning on stderr. Exits 1 if no heading matches.
- `--format <fmt>` — output format: `text` (default), `md` (markdown with frontmatter), `json` (structured object with `content` and `sections` index).

Output: plain text (not JSON) by default. Cache file: `~/.spovishun-cache/claude-md.json`.

Override cache directory via `SPOVISHUN_CACHE_DIR` env var:
```bash
SPOVISHUN_CACHE_DIR=/tmp/my-cache node get-claude-md.js
```

**When to use `--section`:** prefer targeted section reads when you only need one part of CLAUDE.md (e.g., just commands or just testing rules). This avoids loading the full document into context.

## Environment variables

| Variable | Required | Description |
|---|---|---|
| `NOTION_SKILLS_TOKEN` | One of these two | Notion integration token (checked first) |
| `NOTION_TOKEN` | One of these two | Notion integration token (fallback) |
| `SPOVISHUN_CACHE_DIR` | No | Override default cache directory (`~/.spovishun-cache`) |

## Running tests

Uses built-in `node --test`. Zero external test dependencies.

```bash
# All three suites
npm test

# Unit tests only (no token required)
npm run test:unit

# Integration tests (requires NOTION_TOKEN or NOTION_SKILLS_TOKEN)
npm run test:integration

# Fallback tests (no token required — test error paths)
npm run test:fallback
```

### Integration test behavior

Integration tests skip cleanly when no token is set — they will not fail in CI if the token is absent. When a token is present, they exercise the real Notion API and clean up any created data in `after()` hooks (even if tests fail).

Test tasks use the `[TEST-62]` title prefix for easy identification. If cleanup fails unexpectedly, search Notion for `[TEST-62]` to find and manually archive leftover pages.

### Fallback test behavior

Fallback tests spawn scripts with no token / bogus token and verify error paths. They always run, require no credentials, and are safe to include in CI.

## Zero runtime dependencies

`lib/` has **no** `dependencies` in `package.json`. Node built-ins only (`https`, `fs`, `path`, `os`, `child_process`).
