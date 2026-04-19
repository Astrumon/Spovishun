---
name: notion-data-migrator
description: Use this skill when migrating or importing data into Notion from external sources like Telegram messages, JSON files, CSV data, or Markdown documents. Triggers on "import to Notion", "migrate my notes", "add these to Notion", "convert this to Notion", or bulk page creation requests. Always use this skill when the user wants to move structured or unstructured content into Notion — even if they don't say "migrate", phrases like "put these notes in Notion", "create Notion pages from this", or "move this data to Notion" should trigger it.
---

# Notion Data Migrator

## Migration Workflow

### Step 1: Analyze Source Data
Classify each item before creating anything:

| Type | Characteristics | Notion destination |
|---|---|---|
| `link` | URL only | Links section |
| `tip` | Text + URL | Tips section |
| `note` | Text only | Notes section |
| `code` | Code block | Code section |
| `image` | Image file | Placeholder (API limitation) |

### Step 2: Group by Topic
Cluster related items into topics. Create one subpage per topic.

### Step 3: Create Structure
1. Create parent page (or use existing)
2. Create one subpage per topic
3. Add content to each subpage

**Never create more than 20 pages in a single batch** — Notion MCP may rate-limit.

> Database records (rows) can be batched up to 100 per call. The 20-item limit applies to **pages** only.

## Content Formatting Rules

| Type | Format |
|---|---|
| Tip (text + link) | `> Tip text here\n[Source label](url)` |
| Link (URL only) | `[Descriptive label](url)` — never raw URLs |
| Code | ` ```kotlin\ncode\n``` ` — always specify language |
| Image | `📎 Add image manually: filename.jpg` — API cannot upload images |

## Batch Creation Pattern

```
notion-create-pages(
  parent: { type: "page_id", page_id: "parent-id" },
  pages: [
    { properties: { title: "Kotlin Tips" }, content: "..." },
    { properties: { title: "Architecture Notes" }, content: "..." },
  ]
)
```

## Migrating to a Database

When source data maps to structured records:
1. Fetch DB schema first — get exact property names and valid option values
2. Map each source field to a DB property
3. Use `data_source_id` parent (not `page_id`)
4. Batch up to 100 records per `notion-create-pages` call
5. After migration, verify a sample with `notion-fetch`
