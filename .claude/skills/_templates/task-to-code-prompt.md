# Task-to-Code Prompt Template

Use this template to generate a self-contained English prompt for Claude Code / Windsurf.
Fill in placeholders from the fetched Notion task.

```
## Context
You are working on SpovishunTelegramBotV2 — a Kotlin Telegram bot.
- Kotlin 2.3.0 / JVM 21 / Gradle Kotlin DSL
- Clean Architecture: domain / data / presentation / di / common
- DI: Koin 3.x (dev/prod profiles)
- DB: PostgreSQL (dev + prod) via Jetbrains Exposed ORM
- Migrations: Flyway (db/migration/postgresql/)
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
