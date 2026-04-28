# Notion IDs — Spovishun Workspace

Single source of truth for all Notion IDs used by Spovishun skills and scripts.
Never hardcode IDs from memory — read this file when an ID is needed.

## Board / Task Management

| Resource | ID |
|---|---|
| Board collection (data source) | `3193462f-68a9-80b8-99b9-000bcbf3b536` |
| Notion API DB ID (raw) | `3193462f68a980d69ec9c7ccc6329b88` |
| Board page (task kanban) | `3193462f68a980f1b43bc1e201189bfd` |

The hyphenated form (`3193462f-68a9-...`) is what scripts pass to `--data-source-id`.
The compact form (`3193462f68a9...`) is what MCP tools use for `database_id`.

## Workspace Root Pages

| Page | ID |
|---|---|
| Spovishun (workspace root) | `3183462f68a9803aa93ae34eb81d2659` |
| Documentation root | `3193462f68a981b79936e2e45291df85` |
| CLAUDE.md — Rules for AI | `31c3462f68a9819c8150ff31d729293e` |
| Plan | `31c3462f68a98029a084df47ae579e2b` |
| Claude Code Cheatsheet | `3343462f68a98195bf12c7c0a183f629` |

## Documentation Category Group Pages

New documentation articles are records in the inline database of the matching category group page (not standalone pages).

| Category | Group Page ID | Collection ID |
|---|---|---|
| Architecture | `33c3462f68a9819894a4df73c3b7d9fe` | `collection://b640a79f-ed87-4e14-9f7f-796065d03364` |
| Database | `33c3462f68a9817e83aef4f1a912a8dd` | `collection://74e2c987-7021-4d70-8a4f-dc04e82269b4` |
| Testing | `33c3462f68a98108b41cf3b5c83610fb` | `collection://af9016e6-c28e-4962-8976-4ba43bb4b419` |
| CI/CD | `33c3462f68a98146bf26cc0e5f5c2799` | `collection://ed906931-fd5e-4033-93e1-7aaf43873438` |
| AI Tools | `33c3462f68a981439024cf50673df3a7` | `collection://1dc936bc-7068-41f8-a93f-457109111c5f` |
| Other | `33c3462f68a9819c97cffd4d1ae31db4` | no inline DB — has sub-pages |

Other sub-pages under Documentation:

| Page | ID |
|---|---|
| Learning Materials | `31d3462f68a981d1b134eebd436830eb` |
| Skill Testing Zone | `3383462f68a98118b6bdee9e55e88b8a` |

## API

| Setting | Value |
|---|---|
| Notion API version | `2022-06-28` |

Use this version header for any direct Notion API calls.
