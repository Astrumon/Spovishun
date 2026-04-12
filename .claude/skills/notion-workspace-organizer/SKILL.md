---
name: notion-workspace-organizer
description: Use this skill when reorganizing a Notion workspace, moving pages between parents, building navigation hubs, standardizing page names, or creating a logical hierarchy. Triggers on "organize my Notion", "move this page to", "restructure my workspace", "create a hub page", "clean up Notion", or any request to tidy, reorder, or restructure Notion pages and sections. Always use this skill before moving pages or auditing workspace structure — even for simple moves or renames.
---

# Notion Workspace Organizer

You are an expert at structuring Notion workspaces for clarity, navigability, and long-term maintainability. You help users build logical hierarchies and clean navigation.

## Workspace Audit Workflow

### Step 1: Map the Current Structure
1. Fetch the root/top-level pages to understand existing structure
2. Identify orphaned pages (no clear parent)
3. Note naming inconsistencies (mixed languages, emoji in titles, inconsistent casing)
4. Flag deep nesting (>3 levels is usually too deep)

### Step 2: Propose New Structure
Before moving anything, present a clear before/after plan:
```
Current:                    Proposed:
Projects/                   Projects/
  Spovishun/                  Spovishun/
    old docs/                   Documentation/
    random page                 Board/
  untitled/               Notes/
random stuff/             Resources/
```
Always get confirmation before executing moves.

### Step 3: Execute Moves
```
notion-move-pages(
  page_or_database_ids: ["page-id-1", "page-id-2"],
  new_parent: { type: "page_id", page_id: "target-parent-id" }
)
```
- Move up to 100 pages per call
- Moving to workspace level makes pages private — avoid unless intentional
- Data sources (collection://) cannot be moved individually

## Hub Page Pattern

A hub page is a navigational landing page for a project or topic.

```
[Project description — 1-2 sentences]
---
## Overview
<page url="...">Board</page>
<page url="...">Documentation</page>
<page url="...">Resources</page>

## Quick Links
- **GitHub:** [link]
- **Live:** [link]

## Quick Reference
[Key info table or command list]
```

## Naming Conventions

| Rule | Good | Bad |
|---|---|---|
| Sentence case | `Architecture & Patterns` | `architecture & patterns` |
| No emoji in title | `Documentation` | `Documentation` |
| Descriptive, not vague | `Learning Materials` | `stuff` |
| English or Ukrainian — pick one per workspace | consistent | mixed |

## Hierarchy Depth Guidelines

- **1 level**: Top-level areas (Projects, Notes, Resources)
- **2 levels**: Projects or topic folders
- **3 levels**: Specific pages within a project
- **4+ levels**: Warning — consider flattening or using databases instead

## When to Use Database vs. Page Hierarchy

Use a **database** when:
- Content is repeated/structured (tasks, links, books)
- You need filtering, sorting, or status tracking
- Items have properties beyond just title + content

Use a **page hierarchy** when:
- Content is unique/freeform (docs, notes, architecture)
- Structure is more like a book than a spreadsheet
