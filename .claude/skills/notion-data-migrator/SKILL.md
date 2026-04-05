---
name: notion-data-migrator
description: Use this skill when migrating or importing data into Notion from external sources like Telegram messages, JSON files, CSV data, or Markdown documents. Triggers on "import to Notion", "migrate my notes", "add these to Notion", "convert this to Notion", or bulk page creation requests. Always use this skill when the user wants to move structured or unstructured content into Notion — even if they don't say "migrate", phrases like "put these notes in Notion", "create Notion pages from this", or "move this data to Notion" should trigger it.
---

# Notion Data Migrator

You are an expert at importing and migrating content into Notion. You classify, structure, and batch-create pages efficiently while preserving the original information.

## Migration Workflow

### Step 1: Analyze Source Data
Before creating anything, classify each item:

| Type | Characteristics | Notion destination |
|---|---|---|
| `link` | URL only | Links section |
| `tip` | Text + URL | Tips section |
| `note` | Text only | Notes section |
| `code` | Code block | Code section |
| `image` | Image file | Placeholder (API limitation) |

### Step 2: Group by Topic
Cluster related items into topics. Create one subpage per topic.

Common topic indicators:
- Kotlin/code keywords → Programming
- Notion/productivity → Tools & Workflow
- Architecture/design → Architecture
- Links to docs/guides → Resources

### Step 3: Create Structure
1. Create parent page (or use existing)
2. Create one subpage per topic
3. Add content to each subpage

**Never create more than 20 pages in a single batch** — Notion MCP may rate-limit.
Use multiple `notion-create-pages` calls if needed.

> Note: database records (rows) can be batched up to 100 per call. The 20-item limit applies to **pages** only.

## Content Formatting Rules

### Tips (text + link)
```
> Tip text goes here — explain the value
[Source label](https://url.com)
```

### Links (URL only)
```
[Descriptive label](https://url.com)
```
- Always add a descriptive label — never raw URLs

### Code Snippets
```kotlin
code here
```
- Always specify language for syntax highlighting

### Images
```
Add image manually: filename.jpg
```
- Notion API cannot upload images — always leave a placeholder

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

## Post-Migration Checklist
- [ ] All items classified and placed
- [ ] Image placeholders added for any images
- [ ] Links have descriptive labels
- [ ] Code blocks have language specified
- [ ] Parent page updated with links to new subpages
- [ ] Summary provided to user: X items migrated, Y topics created
