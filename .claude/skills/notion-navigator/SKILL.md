---
name: notion-navigator
description: >
  Reference map of the Spovishun Notion workspace. Use this skill to instantly find
  the correct page when creating, updating, or linking documentation.
  Triggers on: "де в Notion", "яка сторінка", "куди додати документацію",
  "notion page for", "where in notion", "find the notion page".
  Refresh triggers: "оновити карту", "refresh navigator", "перевір notion структуру", "sync notion map".
  Load silently before any Notion write operation.
last_verified: 2026-04-04
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

## Documentation — Child Pages

All documentation lives under **Documentation** (`3193462f68a981b79936e2e45291df85`).

| Page | ID | What belongs here |
|------|----|-------------------|
| CLAUDE.md — Rules for AI | `31c3462f68a9819c8150ff31d729293e` | Project rules for AI assistant; commands, patterns, layer rules |
| 🏬 Architecture | `3193462f68a981a8ae94fcc8669b0eda` | Clean Architecture overview, data flow, layer responsibilities, ResultContainer, coroutine patterns |
| 🔧 Technologies / Frameworks | `3193462f68a981b5a080d8d46bb2b350` | Tech stack reference (Kotlin 2.3.0, Koin, Exposed, Flyway, etc.) |
| 📐 Code Convention | `3193462f68a98193ac1cfab8daf0269c` | Kotlin naming, coding style, patterns |
| 🐘 PostgreSQL & pgAdmin Setup | `3233462f68a98151a0bedf3448c446cd` | Dev database setup, Docker, pgAdmin config |
| 🔄 Database Migrations (Flyway) | `3243462f68a981c9bddbefebc5153fde` | Migration workflow, Flyway versioning, `generateMigration` task |
| 🗄️ Database Tables | `32f3462f68a9810c965efe50a7a53a52` | Schema documentation; add new module tables here as sub-pages |
| 🪝 Claude Code — .claude/ Infrastructure | `3303462f68a98175bdf8f79f9103a902` | Hooks (notion-task-inject, session-end, session-start), agents, rules |
| 🛠️ Claude Code — Installed Skills | `32b3462f68a981719106c6b1d82f906c` | Skills registry (auto-synced); reference for available skills |
| 🧪 E2E Tests — Setup & Guide | `3313462f68a98161a27bc3fd079a9442` | E2E test environment, Telegram API test setup |
| ⚙️ GitHub Actions — CI/CD | `3313462f68a981199b92c9184221dee8` | CI/CD pipeline, workflow files, secret management |
| 🔀 GitHub Repository Setup | `31a3462f68a981919e43cefbe8056bd3` | Branch structure, Conventional Commits, branch protection |
| 📚 Learning Materials | `31d3462f68a981d1b134eebd436830eb` | Reference links: SQL, Exposed, PostgreSQL, Docker, Testing |
| 💡 Idea Planning Pipeline | `3383462f68a9817daabcfce958998cba` | How-to guide for idea-brainstormer → solution-designer → task-decomposer |

### Database Tables — Sub-pages

| Page | ID | What belongs here |
|------|----|-------------------|
| 🤖 Bot Module | `3313462f68a98145bbd2f8398bec9bab` | chats, members, groups, group_members tables |
| *(future modules)* | — | Add new module sub-page here (e.g., `:server`, `:api`) |

### Claude Code Infrastructure — Sub-pages

| Page | ID | What belongs here |
|------|----|-------------------|
| notion-task-inject (hook) | `3303462f68a981d49731ea0245fd39c8` | Hook internals: branch caching, status update logic |
| session-end (hook) | `3373462f68a981b3a677e0fe2162362f` | session-end hook details, trigger patterns |

---

## Board Collection

Used for querying the task board (Kanban) — needed by `task-decomposer` and `notion-spovishun-task-manager`.

| Resource | ID |
|----------|----|
| Board collection (data source) | `3193462f-68a9-80b8-99b9-000bcbf3b536` |

---

## Decision Table: Where to Put New Documentation

| I want to document... | Parent page | ID |
|-----------------------|-------------|-----|
| New architecture pattern or design decision | Architecture | `3193462f68a981a8ae94fcc8669b0eda` |
| New DB table or schema change | Database Tables → (module sub-page) | `32f3462f68a9810c965efe50a7a53a52` |
| New Flyway migration workflow change | Database Migrations | `3243462f68a981c9bddbefebc5153fde` |
| New hook, agent, or rule in `.claude/` | Claude Code — .claude/ Infrastructure | `3303462f68a98175bdf8f79f9103a902` |
| New or updated skill | Claude Code — Installed Skills | `32b3462f68a981719106c6b1d82f906c` |
| Feature how-to guide or step-by-step | Documentation (top-level child) | `3193462f68a981b79936e2e45291df85` |
| Idea planning or pipeline docs | Idea Planning Pipeline | `3383462f68a9817daabcfce958998cba` |
| GitHub / CI/CD workflow change | GitHub Actions — CI/CD | `3313462f68a981199b92c9184221dee8` |
| Tech stack change or new dependency | Technologies / Frameworks | `3193462f68a981b5a080d8d46bb2b350` |
| Code style or naming convention change | Code Convention | `3193462f68a98193ac1cfab8daf0269c` |
| General project rules (for AI or devs) | CLAUDE.md | `31c3462f68a9819c8150ff31d729293e` |
| New feature roadmap item | План розвитку | `31c3462f68a98029a084df47ae579e2b` |

---

## Critical Rules

- **NEVER create a page at the root** — always under Documentation or a specific section
- **NEVER hardcode IDs from memory** — use this map; IDs here are verified against live Notion
- When a topic spans multiple sections (e.g., new feature with DB + architecture changes), create one page and link from the others using Notion mentions
- New module tables always get their own sub-page under Database Tables, never merged into Bot Module
- How-to guides and pipeline documentation go as direct children of Documentation

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
