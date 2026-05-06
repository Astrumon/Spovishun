---
name: notion-workflow-spovishun
description: Project-specific Notion workflow for the Spovishun project. Always use this skill at the start of any task that involves the SpovishunTelegramBotV2 project and Notion — even if it's just reading a page, creating a task, or updating docs. Triggers on any mention of "Spovishun", "SpovishunBot", "TelegramBotV2", "spovishun bot", or any related variation.
---

# Notion Workflow — Spovishun Project

## Workflow

1. Fetch the targeted section of CLAUDE.md for the current operation (see commands below).
2. Identify the operation type and pick the matching row in the Decision Table.
3. If IDs are needed, read `references/data-ids.md` — do not guess or recall IDs from memory.
4. Invoke the routed skill to complete the operation.

## CLAUDE.md Fetch Commands

Fetch silently before responding — no need to announce it:

```
node scripts/notion/get-claude-md.js --section commands       # architecture / commands
node scripts/notion/get-claude-md.js --section testing        # testing conventions
node scripts/notion/get-claude-md.js --section architecture   # source structure / layers
node scripts/notion/get-claude-md.js                          # full read — only when overview needed
```

Use targeted `--section` reads to load only the relevant part and save tokens.

## REST-first hot path

The start-task flow (`notion-task-inject.js` hook + `scripts/notion/*.js`) is 100% Notion REST via `scripts/notion/lib/notion-http.js`. Task content, status updates, and CLAUDE.md reads all happen through these scripts — no MCP is involved. The hook writes the result to `.dev-context/{branch}_prd/` and injects it via `additionalContext` (no tool-call overhead for Claude).

**Do NOT add MCP calls to this path.** MCP is for operations the scripts cannot cover.

## MCP vs REST decision matrix

| Operation | Tool |
|---|---|
| Read task / board / CLAUDE.md (hot path) | `scripts/notion/*.js` (REST) |
| Update task status (hot path) | `scripts/notion/update-status.js` or hook PATCH |
| Free-form semantic search across Notion | MCP `notion-search` |
| Create / update arbitrary page content | MCP via `notion-page-builder` skill |
| Create / query databases | MCP via `notion-database-manager` skill |
| Bulk doc sync | MCP via `update-doc-full` |

## Decision Table

| Operation | Skill |
|---|---|
| Task CRUD on Spovishun board | `notion-spovishun-task-manager` |
| Generic Kanban board operations | `notion-task-board-manager` |
| Create / update Notion pages | `notion-page-builder` |
| Search / read existing content | `notion-content-reader` |
| Create / query databases | `notion-database-manager` |
| Migrate external content | `notion-data-migrator` |
| Workspace structure / moves | `notion-workspace-organizer` |
| Workspace / category / collection IDs | `notion-navigator` |
| Board IDs, CLAUDE.md page ID, data source ID | `references/data-ids.md` |

## Do NOT

- Do NOT load `references/data-ids.md` unless an ID is actually needed — most operations go through `notion-navigator` or the dedicated skill.
- Do NOT duplicate operations handled by a routed skill — delegate and do not re-implement.
- Do NOT hardcode IDs from memory; always look them up from `references/data-ids.md` or `notion-navigator`.

## Error Handling

- If no row in the Decision Table matches, ask the user to clarify the operation type.
- If a reference file is missing, stop and report the exact path.
- If `node scripts/notion/get-claude-md.js` fails, check that `NOTION_API_TOKEN` is set in the environment.

## Related Skills

- `notion-navigator` — full workspace map with all category page IDs and collection IDs
- `notion-spovishun-task-manager` — task CRUD, status updates, board queries
- `notion-page-builder` — creating and updating Notion page content via MCP

## Example Invocation

- User: "Read the current task" → fetch CLAUDE.md commands section, load `notion-spovishun-task-manager`.
- User: "Update the Architecture doc page" → load `notion-navigator` for the correct parent ID, then `notion-page-builder`.
