---
name: notion-task-to-code
description: Converts a Spovishun Notion task into a ready-to-use AI agent prompt for Claude Code or Windsurf. Always use this skill when the user says "зроби промпт для задачі", "згенеруй промпт", "промпт для Claude Code", "підготуй задачу для агента", "запусти агента на задачу", or any request to turn a Notion task into executable instructions for an AI coding agent. Also triggers when user says a task number (e.g. "#19", "задача 18") and asks to start working on it.
---

# Notion Task → Code Prompt

> IDs: see `notion-ids.md`. CLAUDE.md is auto-fetched by `notion-workflow-spovishun`.

## Workflow

### Step 1: Fetch the task
If user gave a task number (e.g. `#19`):
```
Notion:notion-search(
  query: "spovishun-19",
  data_source_url: "collection://3193462f-68a9-80b8-99b9-000bcbf3b536"
)
```
Then fetch the full page by its ID:
```
Notion:notion-fetch(id: "<task-page-id>")
```

### Step 3: Extract task fields
From the fetched task page, extract:
- **Goal** (🎯 Мета) — what the task is about
- **Branch name** (🌿 Назва гілки) — `feature/spovishun-N-xxx`
- **Steps** (📋 Кроки) — ordered list of implementation steps
- **Definition of Done** (✅ DoD) — completion condition
- **🤖 prompt toggle** — existing AI prompt if present (use as base, expand if needed)

### Step 4: Generate the final prompt

Read `.claude/skills/_templates/task-to-code-prompt.md` for the full prompt template.
Fill in all `<placeholders>` from the fetched task fields. Output as a fenced code block.

### Step 5: Present the output
Show the prompt in a code block and offer to update the 🤖 prompt toggle in Notion.

### Step 6: Enter Plan Mode
After presenting the prompt, immediately enter Plan Mode using the `EnterPlanMode` tool.
Use the generated prompt as the planning brief.
Plannotator will intercept `ExitPlanMode` — wait for user approval before proceeding to implementation.
