# Notion Skills Deduplication — Results

## Smoke Tests (spovishun-63)

All trigger checks passed — frontmatter `description:` fields are byte-identical to originals (confirmed via git diff).

| Skill | Representative prompt | Status | Notes |
|---|---|---|---|
| notion-workflow-spovishun | "set up Notion for Spovishun" | ANALYZED | Unchanged — no edits made |
| notion-spovishun-task-manager | "створи задачу для features" | ANALYZED | Frontmatter intact; all 4 Creating steps + 5 page sections present |
| notion-task-board-manager | "show the board" | ANALYZED | MCP examples intact; "Displaying Board State" section removed (non-operational) |
| notion-task-to-code | "зроби промпт для задачі #63" | ANALYZED | Step 0 removed (handled by notion-workflow-spovishun); template externalized to _templates/ |
| notion-page-builder | "create a Notion page" | ANALYZED | Duplicate icon callout removed; MCP flow intact |
| notion-database-manager | "create a Notion database" | ANALYZED | Adding Records preamble compressed; MCP code example intact |
| notion-content-reader | "read this Notion page" | ANALYZED | Intro removed; Understanding Page Output table removed; fetch example intact |
| notion-data-migrator | "migrate data to Notion" | ANALYZED | Batch creation pattern intact; Post-migration checklist removed |
| notion-workspace-organizer | "reorganize my Notion workspace" | ANALYZED | Hub Page and Hierarchy Depth sections removed; core workflow intact |

> Full interactive smoke test (fresh chat, cache-cold) should be run manually per the task DoD.

## Final Size Table

| Skill | Baseline (bytes) | Final (bytes) | Delta | % saved |
|---|---:|---:|---:|---:|
| notion-workflow-spovishun | 1 777 | 1 777 | 0 | 0.0% |
| notion-spovishun-task-manager | 5 797 | 3 096 | -2 701 | 46.6% |
| notion-task-board-manager | 3 913 | 2 365 | -1 548 | 39.6% |
| notion-task-to-code | 3 543 | 2 044 | -1 499 | 42.3% |
| notion-page-builder | 1 974 | 1 740 | -234 | 11.9% |
| notion-database-manager | 3 025 | 2 581 | -444 | 14.7% |
| notion-content-reader | 2 857 | 2 151 | -706 | 24.7% |
| notion-data-migrator | 3 211 | 2 355 | -856 | 26.7% |
| notion-workspace-organizer | 3 129 | 2 343 | -786 | 25.1% |
| **TOTAL (9 skills)** | **29 226** | **20 452** | **-8 774** | **30.0%** |

### Additional files created/modified
| File | Bytes | Notes |
|---|---:|---|
| `.claude/skills/notion-ids.md` | ~500 | New — shared ID reference (not a skill) |
| `.claude/skills/_templates/task-to-code-prompt.md` | ~700 | New — externalized prompt template |
| `.claude/skills/notion-navigator/SKILL.md` | ~8 010 | Trimmed "Related Skills" footer (~180 bytes) |
