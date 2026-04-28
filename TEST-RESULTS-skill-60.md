# technical-documentation-writer Skill — Smoke Test Results (spovishun-60)

Run each scenario in a **fresh Claude session** (cache-cold). Update Status after each run. Timestamp the run date.

Last run: _not yet run_

## Smoke Tests

| Scenario | Prompt | Expected behaviour | Status | Observation |
|---|---|---|---|---|
| A — Generic README | "Write a README for a small Python CLI tool called jsondiff that compares two JSON files." | Uses CLI Tool README template; no Kotlin/Gradle assumption; includes Usage, Options table, Exit Codes, Prerequisites | ⬜ pending | |
| B — Node.js shared lib | "Write a README for a Node.js shared library at scripts/notion/lib/ that is used by scripts in scripts/notion/ and by the hook at .claude/hooks/notion-task-inject.js." | Includes a "Consumers" section listing the two entry points | ⬜ pending | |
| C — CLAUDE.md with decision rules | "Write a CLAUDE.md for a project where developers have to choose between using the Notion MCP tools and local Node.js scripts." | Includes item 9 (decision rules table — scripts vs MCP) | ⬜ pending | |
| D — Regression (Kotlin/Gradle) | "Write a README for my Kotlin/Gradle project." | Quick Start still present; `./gradlew run` or equivalent shown via the Note; no regression | ⬜ pending | |
| E — Project with env vars | "Document my project that uses NOTION_TOKEN and DATABASE_URL." | Includes Configuration table + `.env.example` mention | ⬜ pending | |

## Status legend
- ✅ pass — output matches expected behaviour
- ❌ fail — missing required section or wrong template used
- ⚠️ partial — mostly correct but missing one minor element
- ⬜ pending — not yet tested

## Re-test log (if any FAIL)

| Scenario | Version | Fix applied | Status |
|---|---|---|---|
| | v2 | | |
