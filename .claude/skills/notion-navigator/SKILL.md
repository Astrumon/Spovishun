---
name: notion-navigator
description: "Reference map of the project Notion workspace. Decision table for where to put new documentation by category, refresh workflow, and critical rule to never create standalone pages. Triggers: where in Notion, notion page for, find the notion page, which page to update, де в Notion, яка сторінка, куди додати документацію, оновити карту."
---
# Notion Navigator — Workspace Map

Reference map of the project's Notion workspace. Use this skill to instantly find the correct page when creating, updating, or linking documentation. Load silently before any Notion write operation.

## Decision Table: Where to Put New Documentation

New documentation articles are created as records in the category inline database (not as standalone pages).

| I want to document... | Category | Group Page ID |
|-----------------------|----------|--------------|
| New architecture pattern, design decision, layer change | Architecture | `33c3462f68a9819894a4df73c3b7d9fe` |
| Tech stack change, new dependency, code convention | Architecture | `33c3462f68a9819894a4df73c3b7d9fe` |
| New DB table, schema change, ORM update | Database | `33c3462f68a9817e83aef4f1a912a8dd` |
| Database migration workflow, DB setup | Database | `33c3462f68a9817e83aef4f1a912a8dd` |
| E2E test setup, integration test infrastructure | Testing | `33c3462f68a98108b41cf3b5c83610fb` |
| GitHub Actions workflow, CI pipeline, repo setup | CI/CD | `33c3462f68a98146bf26cc0e5f5c2799` |
| New user-facing feature (new command, scheduler, behaviour change) | Features | `35f3462f68a981419511fb0ea80d1bb4` |
| New or updated skill, hook, agent, rule in .claude/ | AI Tools | `33c3462f68a981439024cf50673df3a7` |
| Idea planning pipeline, Claude Code guides | AI Tools | `33c3462f68a981439024cf50673df3a7` |
| New Epic (multi-task initiative description) | Epics | `3633462f68a981098385fa260e9ce132` |
| Learning resources, reference links | Other/Learning | fetch from workspace root |
| General project rules (for AI or devs) | CLAUDE.md | `31c3462f-68a9-819c-8150-ff31d729293e` |

## Critical Rules

- NEVER create a standalone page — new documentation is a record in the category inline database
- NEVER hardcode IDs from memory — use this map; IDs here are verified against live Notion
- To add a doc article: create a new page in the inline DB of the matching category group page
- When a topic spans multiple categories: create one article in the primary category and mention it from the other

<details>
<summary>Extended: full workspace map, board collection, refresh workflow</summary>

## Workspace Root

Fetch via `notion-fetch` using the workspace root URL to discover top-level structure.

Key resources:
- Board (task kanban) — `36f3462f68a981328625d728cac86ea3` (database_id; queried via REST `/databases/{id}/query`)
- Epics database — `d0c0020049f74b0589979065d8cfe7d3` (database_id; same REST path)
- CLAUDE.md — `31c3462f-68a9-819c-8150-ff31d729293e`

For MCP create flows that need a `data_source_id`, fetch the live collection id from the database first (`<data-source url="collection://...">`); the IDs above are database_ids, not collection ids — they are not interchangeable.

## When to Refresh

Refresh when: new Notion page was created, user mentions an unknown page, or user explicitly asks to refresh/sync the navigator.

## Refresh Workflow

### Step 1: Fetch live structure
```
notion-fetch(id: "<workspace root URL>")
notion-fetch(id: "<documentation root URL>")
```

### Step 2: Compare
For every page URL entry returned:
- Extract title and ID
- Check if it already exists in the map; if missing — add it; if title changed — update; if no longer in Notion — mark as removed

### Step 3: Update this file
Rewrite only changed rows/sections using the Edit tool. Update `last_verified` in frontmatter.

### Step 4: Confirm
Report: "Updated: +N new, ~M changed, -K removed pages."

</details>
