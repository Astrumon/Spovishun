---
name: notion-task-to-code
description: Converts a Spovishun Notion task into a ready-to-use AI agent prompt for Claude Code or Windsurf. Always use this skill when the user says "зроби промпт для задачі", "згенеруй промпт", "промпт для Claude Code", "підготуй задачу для агента", "запусти агента на задачу", or any request to turn a Notion task into executable instructions for an AI coding agent. Also triggers when user says a task number (e.g. "#19", "задача 18") and asks to start working on it.
---

# Notion Task to Code Prompt

> IDs: see `notion-ids.md`. CLAUDE.md is auto-fetched by `notion-workflow-spovishun`.

## Workflow

### Step 1: Fetch the task
```
node scripts/notion/get-task.js <N-or-pageId>
```
Accepts `spovishun-19`, bare `19`, or a 32-char compact pageId.

### Step 2: Fetch CLAUDE.md (targeted)
```
node scripts/notion/get-claude-md.js --section commands       # just the Commands section
node scripts/notion/get-claude-md.js --section testing        # just Testing section
node scripts/notion/get-claude-md.js                          # full read - only when overview needed
```

### Step 4: Generate the final prompt
Read `.claude/skills/_templates/task-to-code-prompt.md` for the prompt template.
Fill in all placeholders from the fetched task fields. Output as a fenced code block.

### Step 6: Enter Plan Mode
After presenting the prompt, immediately enter Plan Mode using the `EnterPlanMode` tool.
Plannotator will intercept `ExitPlanMode` - wait for user approval before proceeding.

<details>
<summary>Extended: extracting task fields (Step 3), presenting output (Step 5)</summary>

### Step 3: Extract task fields
From the fetched task page, extract:
- **Goal** - what the task is about
- **Branch name** - `feature/spovishun-N-xxx`
- **Steps** - ordered list of implementation steps
- **Definition of Done** - completion condition
- **prompt toggle** - existing AI prompt if present (use as base, expand if needed)

### Step 5: Present the output
Show the prompt in a code block and offer to update the prompt toggle in Notion.

</details>
