---
name: notion-task-to-code
description: Converts a Spovishun Notion task into a ready-to-use AI agent prompt for Claude Code or Windsurf. Always use this skill when the user says "зроби промпт для задачі", "згенеруй промпт", "промпт для Claude Code", "підготуй задачу для агента", "запусти агента на задачу", or any request to turn a Notion task into executable instructions for an AI coding agent. Also triggers when user says a task number (e.g. "#19", "задача 18") and asks to start working on it.
---

# Notion Task → Code Prompt

Converts a Spovishun Notion board task into a structured, executable prompt for Claude Code or Windsurf agent.

## Key IDs
| Resource | ID |
|---|---|
| Board collection | `3193462f-68a9-80b8-99b9-000bcbf3b536` |
| CLAUDE.md | `31c3462f68a9819c8150ff31d729293e` |

## Workflow

### Step 1: Load context (silently)
Fetch CLAUDE.md for latest project conventions:
```
Notion:notion-fetch(id: "31c3462f68a9819c8150ff31d729293e")
```

### Step 2: Fetch the task
If user gave a task number (e.g. `#19`):
```
Notion:notion-search(
  query: "spovishun-19",
  data_source_url: "collection://3193462f-68a9-80b8-99b9-000bcbf3b536"
)
```
Then fetch the full page by its ID:
```
Notion:notion-fetch(id: "<task-page-id>")
```

### Step 3: Extract task fields
From the fetched task page, extract:
- **Goal** (🎯 Мета) — what the task is about
- **Branch name** (🌿 Назва гілки) — `feature/spovishun-N-xxx`
- **Steps** (📋 Кроки) — ordered list of implementation steps
- **Definition of Done** (✅ DoD) — completion condition
- **🤖 prompt toggle** — existing AI prompt if present (use as base, expand if needed)

### Step 4: Generate the final prompt

Compose a **self-contained English prompt** ready to paste into Claude Code or Windsurf:

```
## Context
You are working on SpovishunTelegramBotV2 — a Kotlin Telegram bot.
- Kotlin 2.3.0 / JVM 21 / Gradle Kotlin DSL
- Clean Architecture: domain / data / presentation / di / common
- DI: Koin 3.x (dev/prod profiles)
- DB: SQLite (dev) / PostgreSQL (prod) via Jetbrains Exposed ORM
- Migrations: Flyway (db/migration/sqlite/ and db/migration/postgresql/)
- Admin checks: via Telegram API (getChatAdministrators), NOT hardcoded
- chatId scoping: all entities scoped by chatId (composite PKs)
- GitHub: read-only access — deliver changes as diffs or files

## Task: <task title>
Branch: <branch name>

## Goal
<goal from 🎯 section>

## Steps
<numbered steps from 📋 section>

## Definition of Done
<DoD from ✅ section>

## Key files / modules
<inferred from steps and architecture>

## Constraints & conventions
- Follow Clean Architecture layer rules: presentation → domain ← data
- Business logic in Service (domain layer), commands only delegate
- No Dispatchers.IO outside dbQuery {} in DatabaseFactory.kt
- Flyway migrations: generate via MigrationGenerator Gradle task
- Commit format: type: short description (max 72 chars, lowercase, no period)
```

### Step 5: Present the output
Show the prompt in a code block and offer to update the 🤖 prompt toggle in Notion.

### Step 6: Enter Plan Mode
After presenting the prompt, immediately enter Plan Mode using the `EnterPlanMode` tool.
Use the generated prompt as the planning brief.
Plannotator will intercept `ExitPlanMode` — wait for user approval before proceeding to implementation.
