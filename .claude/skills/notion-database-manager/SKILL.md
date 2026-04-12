---
name: notion-database-manager
description: Use this skill when creating Notion databases, designing schemas, adding records to databases, updating database properties, or building relations between Notion databases. Always use this skill for any Notion database work — triggers on "create a Notion database", "add to my Notion DB", "design a schema", "notion table", "notion tracker", "add records to Notion", "update Notion properties", or any request to structure data in Notion as a database.
---

# Notion Database Manager

You are an expert at designing and managing Notion databases. You create clean, well-structured schemas and manage records efficiently via MCP tools.

## Schema Design Principles

### Property Type Selection
| Use case | Property type |
|---|---|
| Main identifier | `TITLE` (required, always one) |
| Short category | `SELECT` |
| Multiple tags | `MULTI_SELECT` |
| Money, count | `NUMBER FORMAT 'dollar'` |
| Dates/deadlines | `DATE` |
| Links | `URL` |
| True/false flag | `CHECKBOX` |
| Cross-table link | `RELATION('data_source_id')` |
| Computed value | `ROLLUP` or `FORMULA` |
| Auto increment | `UNIQUE_ID PREFIX 'X'` |
| Workflow state | `STATUS` |

### Creating a Database
```sql
-- Always double-quote column names
-- Always include exactly one TITLE column
CREATE TABLE (
  "Name" TITLE,
  "Status" SELECT('Backlog':gray, 'In Progress':blue, 'Done':green),
  "Priority" SELECT('High':red, 'Medium':yellow, 'Low':gray),
  "Due Date" DATE,
  "Tags" MULTI_SELECT('feature':blue, 'bug':red),
  "Task ID" UNIQUE_ID PREFIX 'TASK'
)
```

### Adding Records

**ALWAYS fetch the database first to get:**
1. The exact `data_source_id` from `<data-source url="collection://...">`
2. The exact property names (case-sensitive)
3. Available SELECT/STATUS options

```
-- Then create pages under the data source:
parent: { type: "data_source_id", data_source_id: "..." }
properties: {
  "Name": "Task title",
  "Status": "In Progress",
  "date:Due Date:start": "2026-03-15",
  "date:Due Date:is_datetime": 0
}
```

### Updating Records
1. Fetch the page to confirm current values
2. Use `update_properties` with only the fields to change
3. Omitted properties remain unchanged

## Relations

For one-way relation:
```
"Project" RELATION('target_data_source_id')
```

For two-way relation (creates synced property in target DB):
```
"Tasks" RELATION('tasks_ds_id', DUAL 'Project' 'project_synced_id')
```

**Note:** For self-relations, create the DB first, then use `update_data_source` to add the self-referencing relation with the DB's own data source ID.

## Common Mistakes to Avoid
- Never use `database_id` parent when DB has multiple data sources — use `data_source_id`
- Properties named `id` or `url` need `userDefined:` prefix
- Checkbox values must be `"__YES__"` or `"__NO__"`, not booleans
- Date values: always split into `date:PropName:start` + `date:PropName:is_datetime`
- SELECT values must exactly match existing options — adding new options requires `update_data_source`
