# New Task Skill

1. Ask the user for task title and description
2. Look up the next task number from the Spovishun Notion board (delegate to `notion-spovishun-task-manager` skill for board access and task structure)
3. Create the Notion task with proper formatting (title, description, status = Backlog)
4. Create a feature branch from `develop`: `feature/spovishun-{N}-{slug}` where N is the task number and slug is max 3 words, kebab-case
5. Confirm completion to user

Do NOT explore the codebase. Do NOT report on existing tasks.
Do NOT branch from `main` — always from `develop`.