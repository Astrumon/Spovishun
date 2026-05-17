---
name: notion-navigator
description: >
  Reference map of the Spovishun Notion workspace. Use this skill to instantly find
  the correct page when creating, updating, or linking documentation.
  Triggers on: "де в Notion", "яка сторінка", "куди додати документацію",
  "notion page for", "where in notion", "find the notion page".
  Refresh triggers: "оновити карту", "refresh navigator", "перевір notion структуру", "sync notion map".
  Load silently before any Notion write operation.
last_verified: 2026-05-17
---

# Notion Navigator - Spovishun Workspace Map

Use this map before any Notion write operation to pick the correct parent page.
Never guess IDs - use only the IDs listed here.

## Decision Table: Where to Put New Documentation

New documentation articles are created as records in the category inline database (not as standalone pages).

| I want to document... | Category | Group Page ID |
|-----------------------|----------|--------------|
| New architecture pattern, design decision, layer change | Architecture | `33c3462f68a9819894a4df73c3b7d9fe` |
| Tech stack change, new dependency, code convention | Architecture | `33c3462f68a9819894a4df73c3b7d9fe` |
| New DB table, schema change, Exposed ORM update | Database | `33c3462f68a9817e83aef4f1a912a8dd` |
| Flyway migration workflow, PostgreSQL setup | Database | `33c3462f68a9817e83aef4f1a912a8dd` |
| E2E test setup, integration test infrastructure | Testing | `33c3462f68a98108b41cf3b5c83610fb` |
| GitHub Actions workflow, CI pipeline, repo setup | CI/CD | `33c3462f68a98146bf26cc0e5f5c2799` |
| New user-facing feature (new command, scheduler, behaviour change) | Features | `35f3462f68a981419511fb0ea80d1bb4` |
| New or updated command, command flow, user-facing behavior change | Features | `35f3462f68a981419511fb0ea80d1bb4` |
| New or updated skill, hook, agent, rule in .claude/ | AI Tools | `33c3462f68a981439024cf50673df3a7` |
| Idea planning pipeline, Claude Code guides | AI Tools | `33c3462f68a981439024cf50673df3a7` |
| New Epic (multi-task initiative description) | Epics | `3633462f68a981098385fa260e9ce132` |
| Learning resources, reference links | Other/Learning | `31d3462f68a981d1b134eebd436830eb` |
| General project rules (for AI or devs) | CLAUDE.md | `31c3462f68a9819c8150ff31d729293e` |
| New feature roadmap item | Plan | `31c3462f68a98029a084df47ae579e2b` |

## Critical Rules

- NEVER create a standalone page - new documentation is a record in the category inline database
- NEVER hardcode IDs from memory - use this map; IDs here are verified against live Notion
- To add a doc article: create a new page in the inline DB of the matching category group page
- When a topic spans multiple categories: create one article in the primary category and mention it from the other

<details>
<summary>Extended: workspace map, full category schema, board collection, refresh workflow</summary>

## Workspace Root

| Page | ID |
|------|----|
| Spovishun (root) | `3183462f68a9803aa93ae34eb81d2659` |
| Board (task kanban) | `3193462f68a980f1b43bc1e201189bfd` |
| Documentation | `3193462f68a981b79936e2e45291df85` |
| Plan | `31c3462f68a98029a084df47ae579e2b` |
| Claude Code Cheatsheet | `3343462f68a98195bf12c7c0a183f629` |

## Documentation Category Structure

All documentation lives under Documentation (`3193462f68a981b79936e2e45291df85`).
Organized into category group pages, each containing an inline database of articles.

Top-level items:

| Page | ID | Notes |
|------|----|-------|
| CLAUDE.md - Rules for AI | `31c3462f68a9819c8150ff31d729293e` | Direct child of Documentation |

Category Group Pages:

| Category | Group Page ID | Collection ID |
|----------|--------------|---------------|
| Features | `35f3462f68a981419511fb0ea80d1bb4` | `collection://2500abb2-2973-4e1a-b7ee-0faa2837a97e` |
| Architecture | `33c3462f68a9819894a4df73c3b7d9fe` | `collection://b640a79f-ed87-4e14-9f7f-796065d03364` |
| Database | `33c3462f68a9817e83aef4f1a912a8dd` | `collection://74e2c987-7021-4d70-8a4f-dc04e82269b4` |
| Testing | `33c3462f68a98108b41cf3b5c83610fb` | `collection://af9016e6-c28e-4962-8976-4ba43bb4b419` |
| CI/CD | `33c3462f68a98146bf26cc0e5f5c2799` | `collection://ed906931-fd5e-4033-93e1-7aaf43873438` |
| AI Tools | `33c3462f68a981439024cf50673df3a7` | `collection://1dc936bc-7068-41f8-a93f-457109111c5f` |
| Epics | `3633462f68a981098385fa260e9ce132` | `collection://a8ac0d93-9d1f-4d34-aa2d-f31d3b3accd0` |
| Other | `33c3462f68a9819c97cffd4d1ae31db4` | no inline DB - has sub-pages |

Other sub-pages:

| Page | ID |
|------|----|
| Learning Materials | `31d3462f68a981d1b134eebd436830eb` |
| Skill Testing Zone | `3383462f68a98118b6bdee9e55e88b8a` |

## Board Collection

| Resource | ID |
|----------|----|
| Board collection (data source) | `3193462f-68a9-80b8-99b9-000bcbf3b536` |
| Epics database (compact) | `d0c0020049f74b0589979065d8cfe7d3` |
| Epics data source | `a8ac0d93-9d1f-4d34-aa2d-f31d3b3accd0` |

Task board now exposes `Epic` (relation → Epics) and `Blocked by` (self-relation, paired with `Blocks`).

## When to Refresh

Refresh when: new Notion page was created, user mentions an unknown page, last_verified is >30 days ago, or user explicitly asks.

## Refresh Workflow

### Step 1: Fetch live structure

```
Notion:notion-fetch(id: "3193462f68a981b79936e2e45291df85")   Documentation root
Notion:notion-fetch(id: "3183462f68a9803aa93ae34eb81d2659")   Workspace root
Notion:notion-fetch(id: "35f3462f68a981419511fb0ea80d1bb4")   Features
Notion:notion-fetch(id: "33c3462f68a9819894a4df73c3b7d9fe")   Architecture
Notion:notion-fetch(id: "33c3462f68a9817e83aef4f1a912a8dd")   Database
Notion:notion-fetch(id: "33c3462f68a98108b41cf3b5c83610fb")   Testing
Notion:notion-fetch(id: "33c3462f68a98146bf26cc0e5f5c2799")   CI/CD
Notion:notion-fetch(id: "33c3462f68a981439024cf50673df3a7")   AI Tools
Notion:notion-fetch(id: "33c3462f68a9819c97cffd4d1ae31db4")   Other
```

### Step 2: Compare

For every page url entry returned:
- Extract title and ID from the URL (last 32 hex chars without dashes)
- Check if it already exists in the map; if missing - add it; if title changed - update; if no longer in Notion - mark as removed

### Step 3: Fetch sub-pages of changed sections only

### Step 4: Update this file

Rewrite only changed rows/sections using the Edit tool. Update last_verified in frontmatter.

### Step 5: Confirm

Report: "Оновлено: +N нових, ~M змінених, -K видалених сторінок."

</details>
