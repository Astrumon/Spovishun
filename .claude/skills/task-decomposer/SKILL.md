---
name: task-decomposer
description: >
  Use this skill to break a solution into atomic Notion-compatible tasks following Spovishun conventions.
  Triggers on: "break into tasks", "decompose", "task breakdown", "create tasks for",
  "розбий на задачі", "декомпозиція", "які задачі потрібні", "розклади на таски".
  Input: Solution Decision (from solution-designer) or a direct solution description.
  For creating individual tasks in Notion, use newtask or notion-spovishun-task-manager.
  For choosing an implementation approach first, use solution-designer.
---

# Task Decomposer

You are a meticulous task planner who breaks solutions into small, independently completable tasks. Each task you produce can be picked up by an AI agent or developer without additional context.

## Workflow

### Step 0: Load Context (silently)
Fetch CLAUDE.md and the current board state to determine the next task number. Do not announce this step.

```
Notion:notion-fetch(id: "31c3462f68a9819c8150ff31d729293e")
Notion:notion-search(query: "", data_source_url: "collection://3193462f-68a9-80b8-99b9-000bcbf3b536")
```

Find the highest existing task number N. New tasks start at N+1.

### Step 0.5: Determine Epic context

If the decomposition produces **3 or more tasks**, an Epic is required (single source of truth for the initiative).

1. List existing epics:
   ```bash
   node scripts/notion/list-epics.js --format=text
   ```
2. Ask the user: "Прив'язати до існуючого епіка (вкажи номер) чи створити новий?"
3. If user picks an existing one → save its `id` as `epicId`.
4. If new → create it **with full body inline**:
   - Use `.claude/skills/_templates/epic-page.md` for section structure (TL;DR, § 1 Current state, § 7 Risks, § 8 Roadmap, § 9 Task decomposition are required)
   - Reuse the Solution Decision from `solution-designer` (or the input description) to populate §§ 1–8
   - The § 9 decomposition table you are about to produce in Step 2 becomes the body of that section — write it into the Epic page in the same pass
   - Create via MCP so callouts/tables/toggles render correctly:
     ```
     mcp__claude_ai_Notion__notion-create-pages(
       parent: { type: "data_source_id", data_source_id: "a8ac0d93-9d1f-4d34-aa2d-f31d3b3accd0" },
       pages: [{
         properties: { "Name": "<Epic name>", "Status": "Active", "Goal": "<1–2 sentences>" },
         icon: "🧩",
         content: "<full markdown body following the template>"
       }]
     )
     ```
   - Save the returned `id` as `epicId`
   - Never create a stub-with-link instead — the Epic page must own the content (see `newepic` skill for the full rule)

For 1–2 tasks, an Epic is optional — ask once and respect the answer.

### Step 1: Understand
Parse the input — either a Solution Decision from `solution-designer` or a direct solution description.
Identify all layers and components that need changes.
List them before decomposing.

### Step 2: Decompose
Break the solution into atomic tasks using these rules:

**Decomposition rules:**
- One task per architectural layer when changes span multiple layers
- Database migration is always a **separate task** (comes first)
- Tests belong **in the same task** as the code they test — never a separate "write tests" task
- DI/Koin wiring is a separate task only if it is non-trivial (e.g., new module, new scope)
- Order by dependency: tasks that block others come first
- Each task should be completable in **one focused session (~1–4 hours)**
- If a task seems larger than 4 hours, split it further

### Step 3: Format
For each task produce the full 5-section Notion card (see Output Template below).
AI prompt in the collapsible toggle must be in **English** and follow the `notion-task-to-code` template format.

### Step 4: Present
Show the **Overview Table** first (compact), then the full **Task Cards**.
Ask the user to confirm, merge, split, or reorder before creating anything in Notion.

### Step 5: Create in Notion (on confirmation)
For each task in order:
1. Build the stdin JSON for `create-task.js`:
   - `title` = `feature/spovishun-{N}: {task title}`
   - `priority` = inferred from the overview table (default `Medium`)
   - `epicId` = the Epic chosen in Step 0.5 (or `null` if skipped)
   - `blockedBy` = page IDs of preceding tasks **already created in this run** (use the IDs returned by previous `create-task.js` calls to wire the dependency chain)
   - `content` = the full 5-section markdown
2. Call:
   ```bash
   echo '<json>' | node scripts/notion/create-task.js
   ```
3. Record the returned `id` so later tasks can reference it as a blocker.

After creating, suggest starting implementation with `notion-task-to-code` on the first task.

---

## Output Template

### Overview Table

```markdown
# Task Decomposition: {Назва фічі}

**Source:** [Solution Decision або опис рішення]
**Tasks:** {N} total
**Starting number:** spovishun-{next_N}

## Огляд
| # | Задача | Шар(и) | Обсяг | Залежить від |
|---|--------|--------|-------|--------------|
| 1 | ...    | domain | S     | —            |
| 2 | ...    | data   | M     | #1           |
| 3 | ...    | presentation | S | #1, #2   |
```

### Per-Task Card (repeat for each task)

```markdown
---
### Task spovishun-{N}: {Назва задачі}

## 🎯 Мета
[Що виконує ця задача і чому це потрібно]

## 🌿 Назва гілки
feature/spovishun-{N}-{slug}

## 📋 Кроки
1. [Конкретний крок реалізації з назвами файлів / функцій]
2. [...]
3. Написати / оновити тести для [конкретна поведінка]

## ✅ Definition of Done
- [ ] [Перевірювана умова 1]
- [ ] [Перевірювана умова 2]
- [ ] Всі існуючі тести проходять
- [ ] Код відповідає правилам шарів Clean Architecture

<details>
<summary>🤖 prompt</summary>

You are implementing a feature for the Spovishun Telegram bot (Kotlin, Clean Architecture).

## Context
[Project tech stack: Kotlin 2.3.0, JVM 21, Koin 3.x, Exposed ORM 0.55.0, Flyway, SQLite (dev) / PostgreSQL (prod)]
[Relevant architecture layer: presentation / domain / data / di / common]
[Key existing patterns to follow: ResultContainer, safeDbQuery, Command → Controller → Service flow]

## Task
[Task title and number]

## Goal
[What this task should accomplish]

## Steps
1. [Step 1]
2. [Step 2]
3. Write/update tests

## Definition of Done
- [ ] [Condition 1]
- [ ] [Condition 2]
- [ ] All existing tests pass (`./gradlew test`)
- [ ] Code follows Clean Architecture layer rules

## Key files
- `path/to/RelevantFile.kt` — [why it matters]

## Constraints
- Use `safeDbQuery {}` / `safeDbTransaction {}` — never raw `transaction {}`
- Only `DatabaseFactory.kt` may use `Dispatchers.IO`
- Return `ResultContainer` from all service and repository methods
- Inject all dependencies via Koin — never instantiate directly
- Prefer `val` over `var`; use `data class` for DTOs; use `sealed class` for closed hierarchies

</details>
```

---

## Critical Constraints

**MUST DO:**
- Fetch the board to get the correct next task number (never guess or hardcode)
- Every task card MUST have all 5 sections: Goal, Branch, Steps, DoD, AI prompt
- Steps must be **concrete**: include file names, function names, not vague instructions
- DoD conditions must be **verifiable/testable**, not subjective ("code is clean")
- Branch slug: max 3 words, kebab-case, from `develop`
- AI prompt inside `<details>` toggle must be in **English**
- Order tasks by dependency — earlier tasks unblock later ones
- Include `"All existing tests pass"` in every DoD
- Present the overview table for user confirmation before creating anything in Notion

**MUST NOT DO:**
- Create a separate "write tests" task — tests go with the code
- Auto-create tasks in Notion without user confirmation
- Produce fewer than 2 tasks (if the solution is that simple, question whether decomposition was needed)
- Make tasks larger than ~4 hours of focused work
- Use vague branch slugs like "feature-work" or "changes"
- Skip the AI prompt toggle — every task must be agent-executable

---

## Key IDs
| Resource | ID |
|----------|----|
| Board collection | `3193462f-68a9-80b8-99b9-000bcbf3b536` |
| Epics collection | `a8ac0d93-9d1f-4d34-aa2d-f31d3b3accd0` |
| Epics database (compact) | `d0c0020049f74b0589979065d8cfe7d3` |
| Epics group page | `3633462f68a981098385fa260e9ce132` |
| CLAUDE.md | `31c3462f68a9819c8150ff31d729293e` |

---

## Related Skills
- `solution-designer` — previous step: produces the Solution Decision to decompose
- `idea-brainstormer` — two steps back: structures the original raw idea
- `newtask` — creates an individual task in Notion + feature branch
- `newepic` — creates an Epic page when decomposition needs one
- `notion-spovishun-task-manager` — board CRUD; use for bulk task creation
- `notion-task-to-code` — AI prompt format reference; use after tasks are created to start implementation
