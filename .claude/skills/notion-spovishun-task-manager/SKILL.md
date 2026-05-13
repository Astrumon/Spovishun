---
name: notion-spovishun-task-manager
description: Use this skill for ALL task management operations in the Spovishun project — creating tasks, updating status, reading the board, assigning branch names, or planning a sprint. Always use this skill when the user mentions "задача", "таск", "борд", "спринт", "створи задачу", "додай таск", "що в борді", "які задачі", "закрий задачу", "переведи в done", or any task/board-related request in the context of the Spovishun project. Combines Notion board operations with Spovishun-specific conventions (task numbering, branch naming, architecture layers). For general (non-Spovishun) Notion board operations, use notion-task-board-manager instead.
---

# Notion Spovishun Task Manager

> Key IDs: see `notion-ids.md`. CLAUDE.md is auto-fetched by `notion-workflow-spovishun`.

**I/O rule:** Reads go through `scripts/notion/` CLI scripts. Writes use MCP (`notion-create-pages`, `notion-update-page`) or `scripts/notion/create-task.js` / `scripts/notion/update-status.js` interchangeably.

## Project Conventions

### Task numbering
- Format: `feature/spovishun-N-short-description`
- `N` - next sequential number (always fetch board to find max N)
- `short-description` - maximum 3 words in kebab-case

### Task title in Notion
- Property name is **Name** (not Title) - case-sensitive
- Format: `feature/spovishun-N: task name`
- No emoji in title - emoji goes in the `icon` field

## Reading the Board

```
node scripts/notion/get-board.js                # JSON (default) - use when processing data
node scripts/notion/get-board.js --format=md    # markdown table - use when displaying to user
```

Display statuses: In progress / Not started / Done (last 3).

## Updating a Task

```
Notion:notion-update-page(
  page_id: "<task-id>",
  properties: { "Status": "In progress" }
)
```

Status flow: `Not started -> In progress -> Done`

<details>
<summary>Extended: creating a task (full 4-step workflow), common mistakes</summary>

## Creating a Task

### Step 1: Next number
Search the board; next N = max existing + 1.

### Step 2: Task data

| Field | Value |
|---|---|
| Name | `feature/spovishun-N: task name` |
| Status | `Not started` |

### Step 3: Page content - all five sections required

```
## Goal
What is the purpose of this task and what outcome is expected.

## Branch name
feature/spovishun-{N}-short-description

## Steps
1. First step
2. Second step

## Definition of Done
A concrete condition - when this task is considered complete.

prompt  (toggle/collapsible)
  Professional English prompt for AI agents (Claude Code / Windsurf).
```

### Step 4: Create with icon

```
Notion:notion-create-pages(
  parent: { type: "data_source_id", data_source_id: "3193462f-68a9-80b8-99b9-000bcbf3b536" },
  pages: [{
    properties: { "Name": "feature/spovishun-N: task name", "Status": "Not started" },
    icon: "...",
    content: "..."
  }]
)
```

## Common Mistakes (Spovishun-specific)
- Property name is **Name**, not Title
- Missing any of the five page sections (Goal / Branch / Steps / DoD / prompt)
- prompt toggle must be in English, professional tone
- Only one task In progress at a time - remind the user if they try to start another

</details>

## After Task Completion (Auto Doc)

When a task moves to `Done`, automatically perform the following steps — no user prompt needed.

### Step 1 — Identify changed files

```bash
git diff develop...HEAD --name-only
```

If the branch is already merged into `main`, use `git diff main~1...main --name-only` instead.

### Step 2 — Classify the change set

| File pattern match | Action |
|---|---|
| New file `presentation/bot/commands/*Command.kt` | New feature OR new command in existing feature |
| New file `presentation/scheduler/*.kt` | Passive component of an existing feature |
| Modified `presentation/bot/commands/*Command.kt` or `BotMessages.kt` | Existing feature update |
| No matches | No doc action needed — exit |

### Step 3 — Apply doc change

**New feature:** create a new record in the Features inline DB. Use the `notion-navigator` skill to get the current Features group page ID. Populate using the template from `.claude/rules/common/feature-documentation.md`, reading the command file and task description as source.

**Feature update:** find the existing Features record by command or scheduler name (search via `mcp__claude_ai_Notion__notion-search`). Patch only the affected section (commands table or functionality bullets). Do not rewrite unaffected sections.

### Step 4 — Report

At the end of the task transition, report what was created or updated (one line). Example:
```
📦 Features doc: created "👋 Registration & Onboarding" record in the Features DB.
```

### Failure handling

If any Notion API call fails: log the intended change in chat and continue — do NOT block the task status transition.
