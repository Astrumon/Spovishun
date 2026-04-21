---
name: notion-task-to-code
description: Converts a Spovishun Notion task into a ready-to-use AI agent prompt for Claude Code or Windsurf. Always use this skill when the user says "зроби промпт для задачі", "згенеруй промпт", "промпт для Claude Code", "підготуй задачу для агента", "запусти агента на задачу", or any request to turn a Notion task into executable instructions for an AI coding agent. Also triggers when user says a task number (e.g. "#19", "задача 18") and asks to start working on it.
---

# Notion Task → Code Prompt

> IDs: see `notion-ids.md`. CLAUDE.md is auto-fetched by `notion-workflow-spovishun`.

## Workflow

### Step 1: Fetch the task
```
Run: node scripts/notion/get-task.js <N-or-pageId>
```
Accepts `spovishun-19`, bare `19`, or a 32-char compact pageId.

### Step 2: Fetch CLAUDE.md
```
Run: node scripts/notion/get-claude-md.js
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
