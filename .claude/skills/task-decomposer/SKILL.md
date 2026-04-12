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
If the user confirms, offer to create tasks using `notion-spovishun-task-manager`.
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
| CLAUDE.md | `31c3462f68a9819c8150ff31d729293e` |

---

## Related Skills
- `solution-designer` — previous step: produces the Solution Decision to decompose
- `idea-brainstormer` — two steps back: structures the original raw idea
- `newtask` — creates an individual task in Notion + feature branch
- `notion-spovishun-task-manager` — board CRUD; use for bulk task creation
- `notion-task-to-code` — AI prompt format reference; use after tasks are created to start implementation
