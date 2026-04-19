---
name: notion-content-reader
description: Use this skill when reading, fetching, or searching for content in Notion. Triggers on "find in Notion", "read my Notion page", "search Notion for", "what's on my Notion page", "fetch from Notion", or any request to retrieve existing Notion content. Always use this skill before attempting to read or navigate any Notion workspace, page, or database.
---

# Notion Content Reader

## Reading Strategy

### Step 1: Identify What to Read
- **Known URL or ID** → use `notion-fetch` directly
- **Topic unknown** → use `notion-search` first, then `notion-fetch` on results
- **Database content** → fetch the database to get the data source URL, then search within it

### Step 2: Fetch Efficiently
```
notion-fetch(id: "page-url-or-id")
```
Always fetch the full page before attempting updates — you need the exact content strings.

### Step 3: Navigate Hierarchy
Notion pages have `<ancestor-path>` showing parent chain. Child pages appear as `<page url="...">` blocks. Fetch root → fetch children as needed. Never assume content — always verify by fetching.

## Search Best Practices

```
notion-search(query: "short descriptive phrase", query_type: "internal")
```

- Use 2–5 word queries — shorter is often better
- Try multiple angles: topic name, page title, key term
- To search within a database: first fetch the DB to get `collection://` URL, then pass as `data_source_url`
- If search times out, try a narrower query

## Reading Database Records

1. **Known Spovishun category** (Architecture, Database, Testing, CI/CD, AI Tools) → use the Collection ID from `notion-navigator` — no need to fetch the DB first.
2. **Otherwise** → fetch the DB page to get `<data-source url="collection://...">` from its schema.
3. Use `notion-search(query: "", data_source_url: "collection://...")` to list all records.
4. Fetch individual records by their URL for full content.

## Output Format

- Summarize the page purpose in 1 sentence
- List key sections and their content concisely
- Highlight actionable items, dates, or status fields
- Provide the direct Notion URL for the user to open
