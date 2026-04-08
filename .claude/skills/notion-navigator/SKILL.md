---
name: notion-navigator
description: >
  Reference map of the Spovishun Notion workspace. Use this skill to instantly find
  the correct page when creating, updating, or linking documentation.
  Triggers on: "де в Notion", "яка сторінка", "куди додати документацію",
  "notion page for", "where in notion", "find the notion page".
  Refresh triggers: "оновити карту", "refresh navigator", "перевір notion структуру", "sync notion map".
  Load silently before any Notion write operation.
last_verified: 2026-04-08
---

# Notion Navigator — Spovishun Workspace Map

Use this map before any Notion write operation to pick the correct parent page.
Never guess IDs — use only the IDs listed here.

---

## Refresh Workflow

Invoke when: user says "оновити карту", "refresh navigator", "перевір notion структуру", or when a new Notion page was created and is not yet in this map.

### Step 1: Fetch live structure
```
Notion:notion-fetch(id: "3193462f68a981b79936e2e45291df85")   ← Documentation root
Notion:notion-fetch(id: "3183462f68a9803aa93ae34eb81d2659")   ← Workspace root
Notion:notion-fetch(id: "33c3462f68a9819894a4df73c3b7d9fe")   ← Architecture category
Notion:notion-fetch(id: "33c3462f68a9817e83aef4f1a912a8dd")   ← Database category
Notion:notion-fetch(id: "33c3462f68a98108b41cf3b5c83610fb")   ← Testing category
Notion:notion-fetch(id: "33c3462f68a98146bf26cc0e5f5c2799")   ← CI/CD category
Notion:notion-fetch(id: "33c3462f68a981439024cf50673df3a7")   ← AI Tools category
Notion:notion-fetch(id: "33c3462f68a9819c97cffd4d1ae31db4")   ← Other category
```

### Step 2: Compare
For every `<page url="...">` entry returned:
- Extract the title and ID from the URL (last 32 hex chars without dashes)
- Check if it already exists in the map below
- If missing → it is a new page that needs to be added
- If title changed → update the map entry
- If a map entry no longer appears in Notion → mark it as removed

### Step 3: Fetch sub-pages of changed sections
Only fetch sub-pages for sections where changes were detected:
```
Notion:notion-fetch(id: "<changed-section-id>")
```

### Step 4: Update this file
Rewrite only the changed rows/sections in this SKILL.md using the Edit tool.
Update `last_verified` in frontmatter to today's date.
Do NOT rewrite sections that had no changes.

### Step 5: Confirm
Report a one-line summary: "Оновлено: +N нових, ~M змінених, -K видалених сторінок."

---

## Workspace Root

| Page | ID | URL |
|------|----|-----|
| 🦀 Spovishun (root) | `3183462f68a9803aa93ae34eb81d2659` | https://www.notion.so/3183462f68a9803aa93ae34eb81d2659 |
| 📋 Board (task kanban) | `3193462f68a980f1b43bc1e201189bfd` | https://www.notion.so/3193462f68a980f1b43bc1e201189bfd |
| 📝 Documentation | `3193462f68a981b79936e2e45291df85` | https://www.notion.so/3193462f68a981b79936e2e45291df85 |
| 🗺️ План розвитку | `31c3462f68a98029a084df47ae579e2b` | https://www.notion.so/31c3462f68a98029a084df47ae579e2b |
| ⌨️ Claude Code Cheatsheet | `3343462f68a98195bf12c7c0a183f629` | https://www.notion.so/3343462f68a98195bf12c7c0a183f629 |

---

## Documentation — Category Structure

All documentation lives under **Documentation** (`3193462f68a981b79936e2e45291df85`).
Documentation is organized into **category group pages**, each containing an **inline database** of articles.

### Top-level items

| Page | ID | Notes |
|------|----|-------|
| CLAUDE.md — Rules for AI | `31c3462f68a9819c8150ff31d729293e` | Project rules for AI assistant; direct child of Documentation |

### Category Group Pages

Each category page hosts an inline database. New documentation articles are created as **records in the database**, not as standalone sub-pages.

| Category | Group Page ID | Inline DB ID | Collection ID | What belongs here |
|----------|--------------|--------------|---------------|-------------------|
| 🏗️ Architecture | `33c3462f68a9819894a4df73c3b7d9fe` | `c4cea10d5e4d4ad6a4f226e1022eb49a` | `collection://b640a79f-ed87-4e14-9f7f-796065d03364` | Clean Architecture, layers, patterns, ResultContainer, Koin DI, code conventions, tech stack |
| 🗄️ Database | `33c3462f68a9817e83aef4f1a912a8dd` | `e1e21982827642a3a56d2ea602a0170e` | `collection://74e2c987-7021-4d70-8a4f-dc04e82269b4` | PostgreSQL setup, Flyway migrations, schema/tables, Exposed ORM |
| 🧪 Testing | `33c3462f68a98108b41cf3b5c83610fb` | `c017b7d0138c400ab5f5e52f85dfd7bf` | `collection://af9016e6-c28e-4962-8976-4ba43bb4b419` | E2E tests, integration tests, test setup, test environment |
| ⚙️ CI/CD | `33c3462f68a98146bf26cc0e5f5c2799` | `a565714f075f4b7b89dec10812dbe7f4` | `collection://ed906931-fd5e-4033-93e1-7aaf43873438` | GitHub Actions workflows, repo setup, branch protection, CI pipelines |
| 🤖 AI Tools | `33c3462f68a981439024cf50673df3a7` | `d061b5d0abda4c6f82b3bf27b7b4eec7` | `collection://1dc936bc-7068-41f8-a93f-457109111c5f` | Claude Code skills, hooks, agents, rules, Idea Planning Pipeline |
| 📚 Other | `33c3462f68a9819c97cffd4d1ae31db4` | *(no inline DB — has sub-pages)* | *(no collection)* | Learning materials, skill testing zone |

### Other — Sub-pages

| Page | ID | What belongs here |
|------|----|-------------------|
| 📚 Learning Materials | `31d3462f68a981d1b134eebd436830eb` | Reference links: SQL, Exposed, PostgreSQL, Docker, Testing |
| 🧪 Skill Testing Zone | `3383462f68a98118b6bdee9e55e88b8a` | Temporary test pages for skill testing — create all test content here |

---

## Board Collection

Used for querying the task board (Kanban) — needed by `task-decomposer` and `notion-spovishun-task-manager`.

| Resource | ID |
|----------|----|
| Board collection (data source) | `3193462f-68a9-80b8-99b9-000bcbf3b536` |

---

## Decision Table: Where to Put New Documentation

New documentation articles are created as **records in the category's inline database** (not as standalone pages).

| I want to document... | Category group page | Group Page ID |
|-----------------------|---------------------|--------------|
| New architecture pattern, design decision, layer change | 🏗️ Architecture | `33c3462f68a9819894a4df73c3b7d9fe` |
| Tech stack change, new dependency, code convention | 🏗️ Architecture | `33c3462f68a9819894a4df73c3b7d9fe` |
| New DB table, schema change, Exposed ORM update | 🗄️ Database | `33c3462f68a9817e83aef4f1a912a8dd` |
| Flyway migration workflow, PostgreSQL setup | 🗄️ Database | `33c3462f68a9817e83aef4f1a912a8dd` |
| E2E test setup, integration test infrastructure | 🧪 Testing | `33c3462f68a98108b41cf3b5c83610fb` |
| GitHub Actions workflow, CI pipeline, repo setup | ⚙️ CI/CD | `33c3462f68a98146bf26cc0e5f5c2799` |
| New or updated skill, hook, agent, rule in `.claude/` | 🤖 AI Tools | `33c3462f68a981439024cf50673df3a7` |
| Idea planning pipeline, Claude Code guides | 🤖 AI Tools | `33c3462f68a981439024cf50673df3a7` |
| Learning resources, reference links | 📚 Other → Learning Materials | `31d3462f68a981d1b134eebd436830eb` |
| General project rules (for AI or devs) | CLAUDE.md | `31c3462f68a9819c8150ff31d729293e` |
| New feature roadmap item | План розвитку | `31c3462f68a98029a084df47ae579e2b` |

---

## Critical Rules

- **NEVER create a standalone page** — new documentation is a **record in the category's inline database**
- **NEVER hardcode IDs from memory** — use this map; IDs here are verified against live Notion
- To add a doc article: create a new page in the inline DB of the matching category group page
- When a topic spans multiple categories (e.g., new feature with DB + architecture changes), create one article in the primary category and mention it from the other
- Learning Materials and Skill Testing Zone are the only standalone sub-pages (under Other)

---

## Related Skills
- `notion-page-builder` — creates/updates pages using IDs from this map
- `notion-spovishun-task-manager` — uses the Board collection ID for task CRUD
- `technical-documentation-writer` — writes documentation pages placed using IDs from this map
- `task-decomposer` — uses Board collection ID to determine next task number

---

## When to Refresh

Refresh this map when:
- A new Notion page was created (any session where `notion-page-builder` ran)
- The user mentions a Notion page that is not in this map
- `last_verified` is more than 30 days ago
- The user explicitly asks to refresh
