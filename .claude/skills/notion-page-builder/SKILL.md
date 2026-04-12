---
name: notion-page-builder
description: Use this skill when creating or updating Notion pages via MCP. Triggers on requests to create pages, update content, add structured data, set icons, migrate content into Notion, or any operation involving Notion MCP tools. Always consult this skill before any Notion MCP operation — even simple ones like "create a page" or "add content to Notion".
---

# Notion Page Builder

You are an expert at creating well-structured, visually organized Notion pages via MCP tools.

## Workflow: Creating Pages

1. Create page with `Notion:notion-create-pages` — pass `icon` directly in the page object (no separate patch needed)
2. Add content using Notion-flavored Markdown in the `content` field

> The `icon` field is supported natively in `notion-create-pages`. A separate `API-patch-page` call for the icon is **no longer needed**.

## Page Icon Rules
- NEVER put emoji in the page title
- ALWAYS pass icon in the `icon` field of the page object during creation
- Pages without a parent cannot have icons set via API — always create under existing parent

## Content Structure (Standard Order)
```
## 💡 Tips
> Tip content with optional link
[Source](https://example.com)

## 🔗 Links
[Label](https://url.com)

## 📝 Notes
Free-form explanations

## 💻 Code
```kotlin
code here
```
```

## Naming Conventions
- Titles: sentence case, clean text only, no brackets or prefixes
- Use descriptive, searchable names

## Important Limitations
- `replace_content` fails if child pages would be deleted — always include `<page url="..."/>` references
- Use `insert_content_after` to add content without touching existing children
- Images cannot be uploaded via API — use placeholder: `📎 Add image manually: filename.jpg`

## Database Schemas
- Always fetch database schema before creating entries in it
- Use exact property names from the schema
- Date properties: split into `date:prop:start` and `date:prop:is_datetime`
