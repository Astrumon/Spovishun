---
name: notion-task-board-manager
description: Use this skill when managing tasks on any Notion Kanban board — creating tasks with correct structure, updating statuses, reading board state, or planning work. Triggers on "create a task", "update task status", "what's in progress", "show the board", "plan a sprint", "add to backlog", or any request to interact with a Notion task board. For Spovishun-specific operations (numbered branches, CLAUDE.md conventions), use notion-spovishun-task-manager instead. Always use this skill before any Notion board operation to ensure correct schema fetching, property naming, and task structure.
---

# Notion Task Board Manager

## Step 0: Always Fetch Board Schema First

Before any board operation, fetch the board to get:
1. Exact `data_source_id` from `<data-source url="collection://...">`
2. Exact property names (case-sensitive)
3. Available SELECT/STATUS option values

```
Notion:notion-fetch(id: "<board-page-url>")
```

Never assume property names — always verify.

---

## Reading the Board

```
Notion:notion-search(
  query: "<project prefix or keyword>",
  data_source_url: "collection://<data_source_id>"
)
```

> `notion-search` returns **titles only** — Status not included. Fetch each page individually for Status: `Notion:notion-fetch(id: "<page-id>")`

Status values: `Not started` / `Backlog` — `In progress` — `Done`

---

## Creating a Task

Pass `icon` directly in `notion-create-pages` — no separate `API-patch-page` needed:

```
Notion:notion-create-pages(
  parent: { type: "data_source_id", data_source_id: "<id>" },
  pages: [{
    properties: {
      "Title": "Task title",
      "Status": "Not started"
    },
    icon: "✨",
    content: "<structured content>"
  }]
)
```

Every task page must include:

```
## 🎯 Goal
What this task achieves and why it matters.

## 📋 Steps
1. First step
2. Second step

## ✅ Definition of Done
> Clear condition — when is this task considered complete.
```

---

## Updating a Task

```
Notion:notion-update-page(
  page_id: "<task-id>",
  properties: { "Status": "In progress" }
)
```

Status flow: `Not started → In progress → Done`

---

## Critical Rules
- Use `data_source_id` parent — never `database_id`
- Never emoji in task title — use `icon` field
- Always fetch schema first — property names are case-sensitive
