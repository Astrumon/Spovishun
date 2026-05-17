---
name: newepic
description: >
  Use this skill to create a new Epic page in the Spovishun Notion Epics database.
  Triggers on: "новий епік", "створи епік", "new epic", "create epic", "додай епік".
  An Epic groups multiple related tasks (typically 3+) under a single goal AND owns
  the full research/specification body inline. Do NOT create a stub that links elsewhere.
  For creating individual tasks (with optional link to an existing Epic), use `newtask`.
  For decomposing a solution into many tasks under a new Epic, use `task-decomposer`.
---

# New Epic Skill

Create a new Epic record in the Spovishun Epics database (under Documentation). The Epic page **is** the epic — it owns the full research/spec body. Never create a stub record that points to a separate page via `Related Notion task` — that field is for the originating *task* (e.g. `spovishun-74`), not for "see content over there".

---

## Step 1: Gather epic info

Ask the user (if not already provided):
1. **Name** — short, descriptive (e.g., "Claude Code Skills Plugin")
2. **Goal** — 1–2 sentences for the Goal property (rollup-friendly)
3. **Status** — `Planned` (default), `Active`, or `Completed`
4. **Originating task** (optional) — URL of the research task that produced this epic (e.g. `spovishun-74`). Goes into `Related Notion task`. Leave blank if none.
5. **Icon** (optional) — single emoji; default `🧩`
6. **Body source** — one of:
   - existing Notion page or markdown file the user wants copied in,
   - inline content the user has prepared,
   - or "build from scratch" (skill drafts sections from the template).

If the user already supplied fields in their message, do not re-ask.

---

## Step 2: Compose the body

Open `.claude/skills/_templates/epic-page.md` and use it as the section skeleton. The template lists required sections (TL;DR, § 1 Current state, § 7 Risks, § 8 Roadmap, § 9 Task decomposition) and optional ones.

Adapt sections to the initiative:
- For a small epic (3–5 tasks) you may collapse §§ 3–6 into a single Architecture paragraph
- For a research-heavy epic, keep all 11 sections as in the template
- Body is written in **Notion-flavored markdown** (callouts, tables with `fit-page-width`, `<details>` toggles, `mermaid` blocks)
- Primary language: Ukrainian, matching the rest of the workspace

If the user provided a source page/file:
- Notion page: fetch via `mcp__claude_ai_Notion__notion-fetch(id)` and reuse the markdown verbatim
- Local file: `Read` it directly

Never write a one-liner body and stash the real content elsewhere.

---

## Step 3: Create the epic (primary path — MCP)

Use MCP so the full markdown body is parsed into native Notion blocks (callouts, tables, toggles all render correctly):

```
mcp__claude_ai_Notion__notion-create-pages(
  parent: { type: "data_source_id", data_source_id: "a8ac0d93-9d1f-4d34-aa2d-f31d3b3accd0" },
  pages: [{
    properties: {
      "Name": "<Name>",
      "Status": "<Status>",
      "Goal": "<Goal — short, for the property only>",
      "Related Notion task": "<originating task URL, or omit>"
    },
    icon: "<emoji, default 🧩>",
    content: "<full markdown body composed in Step 2>"
  }]
)
```

Property names are case-sensitive: `Name`, `Status`, `Goal`, `Related Notion task`.

### Fallback (CLI — only for short / programmatic creates)

If the body is just a short paragraph (no tables / callouts / toggles), the CLI path is fine:

```bash
echo '{
  "name": "<Name>",
  "goal": "<Goal>",
  "status": "<Status>",
  "relatedNotionTask": "<URL or omit>",
  "icon": "<emoji>",
  "content": "<plain paragraph text>"
}' | node scripts/notion/create-epic.js
```

The CLI wraps `content` in a single paragraph block — it does NOT parse markdown. For rich bodies use MCP.

---

## Step 4: Confirm to user

Report:
- Epic created: **<Name>** (with the Notion URL)
- Status: `<Status>` · Icon: `<emoji>` · Body sections: `<list of major sections>`
- Suggested next step: create tasks under this epic with `newtask` (the epic picker will list it), or run `task-decomposer` if you already have a Solution Decision.

---

## Do NOT

- Do NOT create a Stub Epic (short body + `Related Notion task` pointing to a "real" page elsewhere). The Epic page must own its content.
- Do NOT create an Epic for a single isolated task — use `newtask` directly
- Do NOT skip the Goal property — every Epic needs a clear "why"
- Do NOT duplicate an existing Epic — run `node scripts/notion/list-epics.js --format=text` first if unsure
- Do NOT use the CLI path for bodies with tables, callouts, or toggles — they will be flattened into raw text

---

## Related Skills

- `newtask` — create an individual task; offers epic selection from the list
- `task-decomposer` — break a solution into tasks; auto-creates an Epic if 3+ tasks (uses the same template)
- `notion-spovishun-task-manager` — list, filter, and update epics/tasks

## Key IDs

| Resource | ID |
|---|---|
| Epics data source | `a8ac0d93-9d1f-4d34-aa2d-f31d3b3accd0` |
| Epics database (compact) | `d0c0020049f74b0589979065d8cfe7d3` |
| Epics group page | `3633462f68a981098385fa260e9ce132` |
| Epic template | `.claude/skills/_templates/epic-page.md` |
