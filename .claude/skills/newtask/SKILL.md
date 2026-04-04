---
name: newtask
description: >
  Use this skill to create a new task on the Spovishun Notion board and prepare the
  corresponding git branch. Triggers on: "new task", "create task", "add task",
  "нова задача", "створи задачу", "додай таск", "нова таска".
  Do NOT use for reading the board, updating status, or sprint planning — use notion-spovishun-task-manager for those.
---

# New Task Skill

Create a new Spovishun task on the Notion board and prepare the git branch.

---

## Step 0: Initialize silently

Fetch CLAUDE.md to load project context:
```
Notion:notion-fetch(id: "https://www.notion.so/31c3462f68a9819c8150ff31d729293e")
```
Do not announce this step.

---

## Step 1: Gather task info

Ask the user (if not already provided):
1. **Task title** — short, imperative, describes the outcome (e.g., "Add member ban command")
2. **Task description** — what is the goal, why it's needed, and expected outcome (2–5 sentences)

If the user already supplied both in their message, use them directly — do NOT ask again.

---

## Step 2: Determine next task number

Fetch the board to find the current highest N:
```
Notion:notion-search(
  query: "",
  data_source_url: "collection://3193462f-68a9-80b8-99b9-000bcbf3b536"
)
```

Scan all task names for the pattern `feature/spovishun-{N}:`, find the maximum N.
Next task number = max N + 1.

If the board is empty or unreadable — stop and inform the user. Do NOT guess or invent a number.

---

## Step 3: Compose task data

| Field | Value |
|---|---|
| Name (property) | `feature/spovishun-{N}: {task title}` |
| Status | `Not started` |
| icon | `✨` (default; user may override) |

Branch name: `feature/spovishun-{N}-{slug}`
- `{slug}` = max **3 words** from the title, kebab-case, English only
- Example: title "Add member ban command" → `feature/spovishun-17-add-member-ban`

---

## Step 4: Build page content

Every new task page must include all five sections:

```
## 🎯 Goal
{Restate the task goal in 1–3 sentences. What outcome is expected?}

## 🌿 Branch name
feature/spovishun-{N}-{slug}

## 📋 Steps
1. {First implementation step}
2. {Second implementation step}
3. ...

## ✅ Definition of Done
> {A concrete, testable condition — when is this task complete?}

🤖 prompt  ← toggle (collapsible)
  {Professional AI agent prompt in English. Include: task context, tech stack (Kotlin, Koin, Exposed, Clean Architecture), relevant files/modules, expected output, conventions from CLAUDE.md.}
```

Rules:
- No emoji in the Name property — emoji goes in `icon` only
- AI prompt must be in **English**, professional, precise — suitable for autonomous agent execution
- Steps should match the architectural layers involved (domain → data → presentation, each change in its own step)

---

## Step 5: Create the task

```
Notion:notion-create-pages(
  parent: { type: "data_source_id", data_source_id: "3193462f-68a9-80b8-99b9-000bcbf3b536" },
  pages: [{
    properties: { "Name": "feature/spovishun-{N}: {task title}", "Status": "Not started" },
    icon: "✨",
    content: "{full page content from Step 4}"
  }]
)
```

⚠️ The property name is **Name** (not Title) — case-sensitive.

---

## Step 6: Create git branch

```bash
git checkout develop
git pull origin develop
git checkout -b feature/spovishun-{N}-{slug}
```

If there is already a branch with this name — inform the user and do NOT overwrite it.

---

## Step 7: Confirm to user

Report:
- Task created: `feature/spovishun-{N}: {task title}` (with Notion URL if available)
- Branch created: `feature/spovishun-{N}-{slug}`
- Current branch is now: `feature/spovishun-{N}-{slug}`

---

## Do NOT

- Do NOT explore the codebase
- Do NOT report on or modify existing tasks
- Do NOT branch from `main` — always from `develop`
- Do NOT guess the task number — always fetch the board first
- Do NOT skip any of the five page sections (Goal, Branch, Steps, DoD, prompt)

---

## Example

User: "нова задача: Add /ban command for admins"

Expected outcome:
- Task `feature/spovishun-{N}: Add /ban command for admins` created on the board with status `Not started`
- Page contains Goal, Branch name, Steps, Definition of Done, and 🤖 prompt toggle
- Branch `feature/spovishun-{N}-add-ban-command` created from `develop` and checked out
- User receives confirmation with task title and branch name

---

## Related Skills

- `notion-spovishun-task-manager` — read board, update status, sprint planning
- `notion-task-to-code` — convert existing task to AI agent prompt
- `git-workflow-pr-writing` — write commit messages and PR descriptions
