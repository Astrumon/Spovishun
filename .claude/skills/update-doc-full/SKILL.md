---
name: update-doc-full
description: >
  Orchestrated documentation sync for Spovishun. Audits git commits over a
  user-specified time range (1w/2w/1m/3m/1y, default 2w), delegates zone
  mapping to the doc-updater agent, presents a structured change-set via
  plannotator for batch confirmation, then applies approved updates to Notion
  via MCP directly. Triggers on "update docs", "sync notion",
  "оновити документацію", "sync docs", "/update-doc-full".
  Invoke as `/update-doc-full` or `/update-doc-full 1m`.
user_invocable: true
---

# update-doc-full

Sync Notion documentation with the actual codebase state by auditing committed git changes over a specified time range.

## Goal

Collect all files touched by commits in the given window, delegate zone mapping + major/critical change detection to the `doc-updater` subagent, present all proposed Notion updates in a single plannotator review for batch user confirmation, then apply each approved update via Notion MCP tools.

**Non-goals:** no code edits, no local file edits, no scan of uncommitted working-directory state, no one-at-a-time y/n confirmation loop.

---

## Step 1 — Parse time-range argument

Read `$ARGUMENTS` (the text passed after `/update-doc-full`). Apply these rules:

- Valid format: `^(\d+)(w|m|y)$` — e.g. `1w`, `2w`, `1m`, `3m`, `1y`
- Default if no argument: `2w`
- Mapping to git `--since`:
  - `Nw` → `--since="N weeks ago"`
  - `Nm` → `--since="N months ago"`
  - `Ny` → `--since="N years ago"`
- If the argument is present but does not match the pattern → stop immediately and print:
  ```
  Invalid range: "<value>"
  Allowed formats: 1w, 2w, 1m, 3m, 1y
  Default (no argument): 2w
  ```

---

## Step 2 — Collect changed files from committed history

Run these two Bash commands in parallel:

```bash
git log --since="<mapped>" --no-merges --oneline
```
```bash
git log --since="<mapped>" --no-merges --name-only --pretty=format: | sort -u | grep -v '^$'
```

Store:
- **commit log** — the `--oneline` output (for agent context)
- **file list** — deduped, sorted list of changed files

**Guard A:** If the `--oneline` output is empty → stop:
```
No commits found in range <range> — nothing to sync.
```

**Guard B:** If the file list is also empty despite non-empty commits (e.g., all commits are empty commits) → stop with the same message.

Do not proceed to subsequent steps if either guard triggers.

---

## Step 2.5 — Pre-map zones from file list (no API calls)

Scan the file list from Step 2 against these patterns and record which categories are affected:

| File pattern | Category | Notion DB ID | Collection ID |
|---|---|---|---|
| `src/main/kotlin/data/db/table/*.kt` | database | `e1e21982827642a3a56d2ea602a0170e` | `collection://74e2c987-7021-4d70-8a4f-dc04e82269b4` |
| `src/main/resources/db/migration/V*__*.sql` | database | `e1e21982827642a3a56d2ea602a0170e` | `collection://74e2c987-7021-4d70-8a4f-dc04e82269b4` |
| `src/main/kotlin/di/*Module.kt` | architecture | `c4cea10d5e4d4ad6a4f226e1022eb49a` | `collection://b640a79f-ed87-4e14-9f7f-796065d03364` |
| `src/main/kotlin/Application.kt` | architecture | `c4cea10d5e4d4ad6a4f226e1022eb49a` | `collection://b640a79f-ed87-4e14-9f7f-796065d03364` |
| `src/main/kotlin/presentation/bot/commands/*Command.kt` | architecture | `c4cea10d5e4d4ad6a4f226e1022eb49a` | `collection://b640a79f-ed87-4e14-9f7f-796065d03364` |
| `src/main/kotlin/presentation/bot/handler/MessageHandler.kt` | architecture | `c4cea10d5e4d4ad6a4f226e1022eb49a` | `collection://b640a79f-ed87-4e14-9f7f-796065d03364` |
| `.github/workflows/*.yml` | cicd | `a565714f075f4b7b89dec10812dbe7f4` | `collection://ed906931-fd5e-4033-93e1-7aaf43873438` |
| `src/test/**/*.kt` | testing | `c017b7d0138c400ab5f5e52f85dfd7bf` | `collection://af9016e6-c28e-4962-8976-4ba43bb4b419` |
| `.claude/hooks/*.js` | aitools | `d061b5d0abda4c6f82b3bf27b7b4eec7` | `collection://1dc936bc-7068-41f8-a93f-457109111c5f` |
| `.claude/agents/*.md` | aitools | `d061b5d0abda4c6f82b3bf27b7b4eec7` | `collection://1dc936bc-7068-41f8-a93f-457109111c5f` |
| `.claude/rules/**/*.md` | aitools | `d061b5d0abda4c6f82b3bf27b7b4eec7` | `collection://1dc936bc-7068-41f8-a93f-457109111c5f` |
| `.claude/skills/**/*.md` | aitools | `d061b5d0abda4c6f82b3bf27b7b4eec7` | `collection://1dc936bc-7068-41f8-a93f-457109111c5f` |
| `.claude/settings.json` | aitools | `d061b5d0abda4c6f82b3bf27b7b4eec7` | `collection://1dc936bc-7068-41f8-a93f-457109111c5f` |

Store: **affected_categories** — deduplicated set of matched category names, their DB IDs, and Collection IDs.

`**/CLAUDE.md` files → no category (Zone 3, no Notion DB fetch needed).

---

## Step 2.6 — Fetch Notion records for affected categories only

For each category in **affected_categories**, search its DB records in parallel using the Collection ID:
```
mcp__claude_ai_Notion__notion-search(query: "", data_source_url: "<Collection ID from Step 2.5>")
```

**Why `notion-search` with `data_source_url`**: `notion-fetch(DB_ID)` returns only the DB schema (columns/properties), not the actual records. The `notion-search` call with `data_source_url` returns the actual page records (Name, Status, Last Updated) stored inside the DB. This is the correct way to check what documentation already exists.

Skip categories not in affected_categories entirely.

From each search result, extract: `Name`, `Status`, `Last Updated` (or equivalent properties) for each record.

Store as:
- **notion_records** — map of `category → list of {Name, Status, Last Updated}` records

**Guard C:** If all searches fail → warn and continue without Notion context (do NOT stop).
If some fail → use what succeeded, note missing categories.

---

## Step 3 — Delegate zone mapping to the doc-updater subagent

Use the Agent tool with `subagent_type: "doc-updater"`. Construct a task prompt that overrides the agent's default file-discovery step. The agent is defined at `.claude/agents/doc-updater.md`.

Prompt template:
```
RANGE-MODE RUN. Do not execute `git diff --name-only HEAD` or `git status`.
Treat the following file list as the complete changed set for this audit:

<insert file list from Step 2, one path per line>

Commit log for context (most recent first):
<insert --oneline output from Step 2>

--- NOTION RECORDS (affected categories only) ---

Cross-reference these records to avoid proposing duplicates and to detect outdated entries.
Only affected categories are listed — others were not fetched.

<for each category in notion_records:>
#### <Category name> (Name | Status | Last Updated)
<insert list of "- Name | Status | Last Updated" records, or "NOT AVAILABLE">

--- END NOTION RECORDS ---

Proceed with zone mapping and produce your standard Documentation Audit Report.
Do not invent file paths — use only those listed above.
```

Wait for the agent to return its full report. If the agent fails or returns output that contains no zone sections → stop:
```
doc-updater failed or returned an unrecognized format.
Raw output:
<agent output>
```

---

## Step 4 — Extract proposed changes into a structured table

Scan the agent's report. For each `- [ ]` line under any "Proposed Notion Updates" subsection, extract one row:

| # | Notion Page | Change Type | Reason | Status |
|---|---|---|---|---|

- **Notion Page** — the target page URL from the zone header in the agent's report. Must contain `notion.so` and a UUID — reject any row where the URL does not match `https://www\.notion\.so/[a-f0-9-]{32,}`.
- **Change Type** — derive from context: New table / New column / New command / New skill / New agent / New rule / New CI / Other
- **Reason** — the agent's checklist description verbatim
- **Status** — always `pending` at this stage

**Exclusion rule:** Skip any row whose proposed target is `CLAUDE.md`, `rules/*.md`, or any local file. These are self-documenting and must not be updated via this skill. The target must be a Notion URL.

If the agent reports zero valid `- [ ]` items across all zones → stop:
```
Documentation already in sync — no proposed changes.
Audit report:
<agent report>
```
Do NOT open plannotator.

---

## Step 5 — Write summary to a temp markdown file

Build the summary file at `.claude/tmp/update-doc-full-<YYYYMMDDHHmmss-millis>.md` (include milliseconds to avoid collision on rapid successive runs).

Ensure `.claude/tmp/` exists before writing — create it via Bash if missing:
```bash
mkdir -p .claude/tmp
```

Template:
```markdown
# Doc Update Summary — <range> (<N> commits, <M> proposed changes)

**Time range:** <human label, e.g. "last 2 weeks"> (git: --since="2 weeks ago")

**Commits:**
<oneline commit list from Step 2>

---

## Proposed Notion Updates

| # | Notion Page | Change Type | Reason | Status |
|---|---|---|---|---|
| 1 | <url> | <type> | <reason> | pending |
...

---

## Legend
Annotate each row's Status cell:
- + = approve (will be applied to Notion)
- - = skip (will be skipped)
- ? = unsure / needs review
- Leave blank = skip

Save and close the editor to return control to the skill.
```

If the write fails → stop and report the attempted path and the error.

---

## Step 6 — Open plannotator for batch confirmation

Invoke the plannotator annotation skill using the Skill tool:
```
Skill: plannotator:plannotator-annotate
Arguments: <absolute path to temp file>
```

This opens an interactive annotation UI in the user's browser. The skill invocation blocks until the user saves and closes.

After plannotator returns, **re-read the temp file** using the Read tool to determine which rows the user approved.

**Approved** = rows where the Status cell contains `+`.
**Skipped** = all other rows (`-`, `?`, blank, or unchanged `pending`).

If plannotator returns an error, or the file cannot be re-read → stop immediately. Do NOT apply any changes.

---

## Step 7 — Apply approved updates via Notion MCP

Before applying, validate each approved row's Notion URL:
- Must match `https://www\.notion\.so/[a-f0-9-]{32,}`
- If the URL fails validation → mark row `skipped: invalid URL` and continue

For each valid approved row (in order):

1. **If Change Type is "Update existing record"** — fetch the target page first to locate the block to update:
   ```
   Notion:notion-fetch(id: "<Notion Page URL>")
   ```
   Skip this fetch for all other change types (new record append does not require it).

2. Apply the change. Use the most appropriate MCP call:
   - To append a new record: `Notion:notion-update-page` with `insert_content_after`
   - To update a block: `mcp__notion__API-patch-block-children`
   - To update page properties: `mcp__notion__API-patch-page`

3. On success → mark row `applied` in memory.
4. On failure → mark row `failed: <error message>` in memory. Continue with the next row. Do NOT retry automatically.

Rules:
- NEVER batch-apply multiple changes in a single MCP call
- NEVER fabricate page URLs — use only what the `doc-updater` agent provided and validated in Step 4
- NEVER apply without iterating through approved rows one by one
- NEVER write to CLAUDE.md, rules/*.md, or any local file in this step

---

## Step 8 — Final report

Print to chat:
```
Doc sync complete (range: <range>)
  Applied:  X
  Skipped:  Y
  Failed:   Z
```

If Z > 0, also print:
```
Failed items:
  - [#N] <Change Type> on <Notion Page URL> — <error>
```

---

## Error handling

| Situation | Action |
|---|---|
| Invalid range format | Show allowed formats, stop |
| Git not available | Report the error, stop |
| Zero commits in range | Print "nothing to sync", stop |
| File list empty despite commits | Print "nothing to sync", stop |
| All Notion fetches in Step 2.6 fail | Warn, continue without Notion records |
| Some Notion fetches in Step 2.6 fail | Use what succeeded, note missing categories |
| `doc-updater` fails or returns garbage | Print raw output, stop |
| Zero valid proposals from agent | Print "already in sync", stop |
| `.claude/tmp/` write fails | Report path and error, stop |
| plannotator exits with error | Stop; do NOT apply anything |
| Approved row has invalid Notion URL | Mark row `skipped: invalid URL`, continue |
| Notion MCP write fails for a row | Mark row `failed: <error>`, continue to next |

---

## Do NOT

- Do NOT modify any code files, migration files, CLAUDE.md, or rules/*.md
- Do NOT scan uncommitted working-directory state — committed history only
- Do NOT fall back to y/n per-item loop if plannotator is unavailable
- Do NOT open plannotator if the agent returned zero valid proposals
- Do NOT fabricate or guess Notion page URLs
- Do NOT apply a row whose Notion URL fails the `notion.so` + UUID validation
- Do NOT push or commit anything

---

## Related Skills

- `update-doc` — simpler one-at-a-time version (uncommitted changes, user-level)
- `doc-updater` — the read-only audit subagent this skill delegates to
- `notion-page-builder` — reference for Notion MCP write patterns
- `notion-content-reader` — use when you need to search Notion before writing
