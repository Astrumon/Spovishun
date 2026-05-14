# Feature Documentation Rules

## When to Write a Feature Page

Write or update a feature page in the Notion Features category whenever:
- A new user-facing command is added
- A new passive component (scheduler, auto-service) is added
- An existing command's behavior, format, or role requirement changes
- A new version introduces or changes feature functionality

Each feature gets its own record in the Features inline DB — never combine features into one page.

## Required Sections (in order)

1. **Призначення** — 1–3 sentences: what the feature does, who uses it, why it exists.
2. **Версія** — semver where the feature was introduced (e.g. `v1.3.0`) + Notion task link if known.
3. **Команди / точки входу** — table with columns: Command/trigger | Role required | Description (one line).
4. **Формат вводу** — argument shape, accepted values, examples. Omit if the command takes no arguments.
5. **Функціонал / поведінка** — bullet list: what happens step-by-step, edge cases, side effects. Passive components (schedulers, auto-services) are described here, not as a separate section.
6. **Діаграма** (optional) — sequence or flow diagram as a Mermaid code block (` ```mermaid `). Include only when the flow is non-trivial (3+ actors or non-obvious ordering). Do NOT use the `diagram-design` skill for feature pages.
7. **Пов'язані фічі** (optional) — links to other Features records this feature depends on or extends.

## Explicitly Forbidden

Do NOT include any of the following in a feature page:
- DI binding tables (module-by-module wiring)
- Full DB schema: column types, constraint names, migration file names
- Repository or Service method signatures or return types
- Kotlin code snippets — except to illustrate a user-visible command syntax
- Internal class names (`*RepositoryImpl`, `*Controller`, `*Scheduler`) in body text — mention only by short role label if unavoidable
- "How implemented" content — that belongs on Architecture category pages

## Rationale

Implementation details change with every refactor; the feature page must stay accurate across those changes. The Architecture and Database categories already document internal patterns and schema.

## Length

Each feature page: ≤ 250 words (not counting tables and diagrams).

## Notion Location

Features go in the **Features** category inline DB (sibling of Architecture, Database, Testing, CI/CD, AI Tools).
Use the `notion-navigator` skill to get the current Features group page ID before creating or updating a record.
