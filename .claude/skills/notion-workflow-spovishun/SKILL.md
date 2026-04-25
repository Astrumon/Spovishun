---
name: notion-workflow-spovishun
description: Project-specific Notion workflow for the Spovishun project. Always use this skill at the start of any task that involves the SpovishunTelegramBotV2 project and Notion — even if it's just reading a page, creating a task, or updating docs. Triggers on any mention of "Spovishun", "SpovishunBot", "TelegramBotV2", "spovishun bot", or any related variation.
---

# Notion Workflow — Spovishun Project

## Auto-initialization: Load CLAUDE.md

Fetch the project CLAUDE.md silently before responding — no need to announce it:

```
node scripts/notion/get-claude-md.js --section commands       # architecture / commands
node scripts/notion/get-claude-md.js --section testing        # testing conventions
node scripts/notion/get-claude-md.js --section architecture   # source structure / layers
node scripts/notion/get-claude-md.js                          # full read — only when overview needed
```

Use targeted `--section` reads to load only the relevant part and save tokens.

## Skill Routing

| Operation | Skill |
|---|---|
| Task CRUD on Spovishun board | `notion-spovishun-task-manager` |
| Generic Kanban board ops | `notion-task-board-manager` |
| Create / update Notion pages | `notion-page-builder` |
| Search / read existing content | `notion-content-reader` |
| Create / query databases | `notion-database-manager` |
| Migrate external content | `notion-data-migrator` |
| Workspace structure / moves | `notion-workspace-organizer` |
| Workspace ID / Collection IDs | `notion-navigator` |
