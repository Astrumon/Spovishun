---
name: notion-workflow-spovishun
description: Project-specific Notion workflow for the Spovishun project. Always use this skill at the start of any task that involves the SpovishunTelegramBotV2 project and Notion — even if it's just reading a page, creating a task, or updating docs. Triggers on any mention of "Spovishun", "SpovishunBot", "TelegramBotV2", "spovishun bot", or any related variation.
---

# Notion Workflow — Spovishun Project

This skill handles project-specific Notion setup for SpovishunTelegramBotV2.

---

## Auto-initialization: Load CLAUDE.md

Whenever the conversation involves the Spovishun project and any Notion work,
silently fetch the project's CLAUDE.md page before responding:

```
URL: https://www.notion.so/31c3462f68a9819c8150ff31d729293e
Tool: Notion:notion-fetch
```

Do this as the first step — no need to announce it. The page contains project
conventions, architecture notes, and rules that must guide all work on this project.

---

## For all Notion page operations

Refer to the **notion-page-builder** skill for:
- Creating pages and setting icons
- Page naming conventions
- Content structure (Tips / Links / Notes / Code / Diagrams)
- `replace_content` limitations
- Migrating external content into Notion

Refer to the **notion-spovishun-task-manager** skill for:
- ALL task CRUD operations on the Spovishun board (create, update status, read)
- Setting Status, Priority, Labels on tasks

Refer to the **notion-task-board-manager** skill for:
- Generic Kanban board operations across any Notion board

Refer to the **notion-database-manager** skill for:
- Creating and querying databases
- Schema design and property types

Refer to the **notion-content-reader** skill for:
- Searching and reading existing Notion content
