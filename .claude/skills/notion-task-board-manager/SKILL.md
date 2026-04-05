---
name: notion-task-board-manager
description: Use this skill when managing tasks on any Notion Kanban board — creating tasks with correct structure, updating statuses, reading board state, or planning work. Triggers on "create a task", "update task status", "what's in progress", "show the board", "plan a sprint", "add to backlog", or any request to interact with a Notion task board. For Spovishun-specific operations (numbered branches, CLAUDE.md conventions), use notion-spovishun-task-manager instead. Always use this skill before any Notion board operation to ensure correct schema fetching, property naming, and task structure.
---

# Notion Task Board Manager

You are a project manager assistant. You help manage tasks on Notion boards, maintain consistent task format, and track progress across any project.

---

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

### Get all tasks
```
Notion:notion-search(
  query: "<project prefix or keyword>",
  data_source_url: "collection://<data_source_id>"
)
```

> ⚠️ `notion-search` returns **titles only** — Status is not included in search results.
> To get Status for specific tasks, fetch each page individually: `Notion:notion-fetch(id: "<page-id>")`
> For a full board display, fetch the most recent 5–10 tasks individually after the initial search.

### Filter by status
After fetching individual pages, check the `<properties>` block for the Status field:
- `Not started` / `Backlog` — planned, not started
- `In progress` — actively being worked on
- `Done` — completed

---

## Creating a Task

### Step 1: Gather info
Before creating, clarify or infer:
- What needs to be built/fixed?
- Which layer does it affect?
- Any dependencies on other tasks?

### Step 2: Build the page

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

### Step 3: Task page structure

Every task should include:

```
## 🎯 Goal
What this task achieves and why it matters.

## 📋 Steps
1. First step
2. Second step
3. ...

## ✅ Definition of Done
> Clear condition — when is this task considered complete.
```

---

## Updating a Task

### Change status
```
Notion:notion-update-page(
  page_id: "<task-id>",
  command: "update_properties",
  properties: { "Status": "In progress" }
)
```

### Typical status flow
```
Not started → In progress → Done
```

---

## Displaying Board State

When the user asks "show the board" or "what's in progress":

```
🔵 In progress (N)
  - Task title 1
  - Task title 2
📋 Not started (N)
  - Task title 3
✅ Done (last 3)
  - Task title 4
```

---

## Common Mistakes to Avoid

| ❌ Wrong | ✅ Correct |
|---|---|
| Emoji in task title | Use `icon` field in `notion-create-pages` |
| Assuming property names | Always fetch schema first |
| Using `database_id` parent | Use `data_source_id` parent |
| Separate `API-patch-page` for icon | Pass `icon` directly in `notion-create-pages` |
| Missing Goal/Steps/DoD sections | Always include full task structure |

---

## Related Skills

- **notion-spovishun-task-manager** — Spovishun-specific board with task numbering and CLAUDE.md
- **notion-database-manager** — modify board schema or add properties
- **notion-content-reader** — search for a task by name
- **notion-page-builder** — page content structure reference
