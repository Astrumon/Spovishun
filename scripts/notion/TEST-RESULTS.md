# Notion Skills — Smoke Test Results

Run these in a **fresh Claude session** after each major change to skills or scripts.
Update the Status column after each run. Timestamp the run date.

Last run: _not yet run_

## Skill Smoke Tests

| Skill | Trigger phrase | Expected behaviour | Status |
|---|---|---|---|
| `notion-spovishun-task-manager` | "що в борді" | Runs `node scripts/notion/get-board.js`, returns board | ⬜ pending |
| `notion-spovishun-task-manager` | "створи задачу X" | Uses MCP `notion-create-pages` | ⬜ pending |
| `notion-spovishun-task-manager` | "переведи в done" | Uses MCP `notion-update-page` | ⬜ pending |
| `notion-task-to-code` | "зроби промпт для задачі #64" | Runs `node scripts/notion/get-task.js 64`, then `get-claude-md.js` | ⬜ pending |
| `notion-content-reader` | "покажи борд" | Shows scripts callout, uses `get-board.js` | ⬜ pending |
| `notion-content-reader` | "знайди сторінку X" | Falls back to MCP `notion-search` | ⬜ pending |
| `notion-navigator` | "де знаходиться X" | Returns ID map from skill — no API call | ⬜ pending |
| `notion-page-builder` | "створи сторінку" | Uses MCP `notion-create-pages` | ⬜ pending |
| `notion-workflow-spovishun` | "start new task" | Triggers hook via UserPromptSubmit | ⬜ pending |
| `notion-task-board-manager` | "show board" | Generic board ops (non-spovishun) | ⬜ pending |

## Status legend
- ✅ pass — correct script/MCP routing, output as expected
- ❌ fail — wrong routing or error
- ⚠️ partial — output correct but routing suboptimal
- ⬜ pending — not yet tested

## Hook Smoke Tests (Part D)

| Scenario | Command | Expected | Status |
|---|---|---|---|
| 1. Picker | `echo '{"hook_event_name":"UserPromptSubmit","prompt":"start new task","session_id":"t1","transcript_path":"/dev/null"}' \| node .claude/hooks/notion-task-inject.js` | `AskUserQuestion` directive + tiered task list | ⬜ pending |
| 2. Cache-hit inject | Active feature branch, fresh `session.lock`, prompt `"задача"` | No HTTP call, lock respected | ⬜ pending |
| 3. Refresh | Prompt `"оновити контекст задачі"` | Bypasses lock, HTTP call made | ⬜ pending |
| 4. Plan auto-save | `node .claude/hooks/notion-task-inject.js --post-exit-plan < <payload>` | `plan.md` written, `session.lock` deleted | ⬜ pending |
| 5. Apply-pick | `node .claude/hooks/notion-task-inject.js --apply-pick <pageId>` | Branch created, `context.md` written, status → In progress | ⬜ pending |
