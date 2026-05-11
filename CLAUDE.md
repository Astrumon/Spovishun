# CLAUDE.md

SpovishunTelegramBotV2 — Kotlin Telegram bot (Clean Architecture).
Stack: Kotlin 2.3.0, JVM 21, Gradle Kotlin DSL + Version Catalog, Koin 3.x, Exposed 0.55.0, Flyway 10.x, PostgreSQL (dev + prod).

## Commands
```bash
./gradlew runDev             # PROFILE=dev — local PostgreSQL + Flyway migrations
./gradlew runProd            # PROFILE=prod — cloud PostgreSQL (Neon) + Flyway migrations
./gradlew test               # unit tests
./gradlew integrationTest    # in-process tests (MockImpl repos)
./gradlew e2eTest            # real Telegram API (skips if env vars unset)
./gradlew generateMigration  # interactive: create next versioned migration file
```

## Source Structure
```
src/main/kotlin/
  Application.kt        — starts Koin + long-polling; initializeKoin() usable in tests
  common/               — pure Kotlin: ResultContainer, exceptions, extensions, logging
  config/               — AppConfig (env var bindings via dotenv)
  domain/               — model/, repository/ (interfaces), service/, cache/
  data/                 — db/ (Exposed impls + tables), memory/ (MockImpl repos), mapper/
  di/                   — Koin modules; selects DB connection string via PROFILE env var
  presentation/         — bot/ (TelegramBot, MessageHandler, commands/), controller/, util/
  tools/                — MigrationGenerator (dev tool, not part of the bot)
```

## Layer Rules
Dependency direction: `presentation → domain ← data`; `common` ← all layers.
See per-layer CLAUDE.md files in `domain/`, `data/`, `presentation/` for details and examples.

## Key Patterns

**ResultContainer** — own sealed class: `Success<T>(val data: T)` / `Failure(val exception: BaseException)`.
Not related to Kotlin's `Result`. Services and repository interfaces return it.
Chain with `.flatMap {}`, resolve with `.fold(onSuccess = {}, onFailure = {})`.
Wrap DB calls with `ResultContainer.catching { }`.

**DB access** — always `safeDbQuery { }` (wraps `dbQuery {}` + `ResultContainer.catching`), never bare `transaction {}` or `ResultContainer.catching { dbQuery { } }` manually.
`safeDbQuery` and `safeDbTransaction` live in `data/db/DatabaseFactory.kt`. Only `DatabaseFactory.kt` may use `Dispatchers.IO`.

**Command flow** — `Command` parses args → calls `Controller` → handles `CommandResponse` via `when` → sends to Telegram.
Controllers return `CommandResponse` (never raw strings). Commands own emoji prefixes and final text assembly.
Never call a `Service` directly from a `Command`.

**Role checks** — `MemberService.hasAdminAccess()` / `hasModeratorAccess()` query the DB.
`BotAdminUtils` (`presentation/util/`) queries Telegram API only to derive initial role on first registration.

**Profile DI** — single `repositoryModule` in `di/RepositoryModule.kt` binds all 5 repositories to `*RepositoryImpl` for both profiles. `PROFILE` controls the DB connection string only (local PostgreSQL for dev, Neon PostgreSQL for prod). MockImpls are used only in integration tests. All bindings use the interface type: `single<MemberRepository> { ... }`.

## Testing
- **Unit** — `mockk<*Repository>()` for Services; `mockk<*Service>()` for Controllers.
  Use `runTest {}`, `coEvery`/`coVerify`, `clearAllMocks()` in `@BeforeTest`.
- **Integration** — extend `BaseIntegrationTest`: real MockImpl repos + real services/commands;
  only `Bot` and `BotAdminUtils` are mocked.
- **e2e** — real Telegram API + real PostgreSQL DB; requires `TEST_BOT_TOKEN`, `TEST_HELPER_BOT_TOKEN`, `TEST_CHAT_ID`, `TEST_ADMINS`, `E2E_DATABASE_URL`.
- Do NOT unit test: Koin modules, `TelegramBot`, `MessageHandler`, `DatabaseFactory`.

## Agent Workflow
- `kotlin-reviewer` — after implementing a feature (reviews Kotlin code quality, architecture, patterns, coroutines)
- `database-reviewer` — after adding a table or migration (reviews DB schema and Exposed usage)
- `doc-updater` — after architectural changes (updates Notion documentation)
- `skill-security-auditor` — before adding a new skill (quality gate: frontmatter, triggers, scope, error handling)
- `code-reviewer` — for PR review with Kotlin-specific checks and verdict section

## Documentation Sync
- `update-doc-full` — range-based Notion doc audit; invoke via `/update-doc-full [range]` (e.g. `1m`); default range `2w`; delegates to `doc-updater` agent, batch-confirms via plannotator, applies via Notion MCP.
- Feature-specific docs (commands, DI bindings, DB schema per feature) → Features page (`notion.so/35b3462f…`). Architecture and Command Flow pages document general patterns only.

## When to use scripts vs MCP (Notion)

| Use case | Preferred tool |
|---|---|
| Board overview | `node scripts/notion/get-board.js` |
| Task by number or pageId | `node scripts/notion/get-task.js <N-or-pageId>` |
| CLAUDE.md page | `node scripts/notion/get-claude-md.js` |
| Create task | `node scripts/notion/create-task.js` or MCP `notion-create-pages` |
| Update status | `node scripts/notion/update-status.js` or MCP `notion-update-page` |
| Semantic search across arbitrary Notion content | MCP `notion-search` (not replicable via scripts) |

## Idea Planning Pipeline
Use these skills to go from a raw idea to implementable tasks:
- `idea-brainstormer` — structures a raw idea into a problem brief (problem statement, scope, risks, feasibility)
- `solution-designer` — compares 2–3 implementation approaches within existing architecture; produces a Solution Decision
- `task-decomposer` — breaks the chosen solution into atomic Notion-compatible tasks with DoD and AI prompts

Pipeline flow: `idea-brainstormer` → `solution-designer` → `task-decomposer` → `newtask` / `notion-task-to-code`
Each skill is standalone — invoke at any stage.

Rules in `.claude/rules/` are always active — they load automatically, no explicit invocation needed.

## Migrations
Files in `src/main/resources/db/migration/postgresql/` — both dev and prod use Flyway against PostgreSQL.
Run `./gradlew generateMigration`, review SQL, commit `Table` object + migration file together.
Never edit a migration that has been applied to any database.

## Branch Convention
`feature/spovishun-{N}-short-description` — branch from `develop`; `main` is production.
