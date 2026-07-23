# CLAUDE.md

SpovishunTelegramBotV2 — Kotlin Telegram bot (Clean Architecture, multi-module).
Stack: Kotlin 2.3.0, JVM 21, Gradle Kotlin DSL + Version Catalog + buildSrc convention plugins,
Koin 4.x, Exposed 0.55.0, Flyway 12.x, PostgreSQL (dev + prod).
Modules: `:common :domain :data :bot :admin-api :app` (see Source Structure); `:app` is the composition root.

## Commands
```bash
./gradlew runDev             # :app, PROFILE=dev — local PostgreSQL + Flyway migrations
./gradlew runProd            # :app, PROFILE=prod — prod PostgreSQL (Docker on Oracle Cloud VM) + Flyway migrations
./gradlew test               # unit tests (all modules)
./gradlew integrationTest    # :app — real services/commands over a real PostgreSQL; skips if E2E_DATABASE_URL unset
./gradlew e2eTest            # :app — real Telegram API + PostgreSQL (skips if env vars unset)
./gradlew generateMigration  # :data — interactive: create next versioned migration file
./gradlew ktlintFormat       # auto-fix formatting (run before committing)
./gradlew ktlintCheck        # verify formatting — CI hard gate
./gradlew detekt             # static analysis — CI non-blocking (see Linting)
```

## Linting & Static Analysis
Two tools with split responsibility — never overlapping:
- **ktlint** (`org.jlleitschuh.gradle.ktlint`) owns **formatting**: indentation, import order/wildcards, syntax.
  Rules come from `.editorconfig`. It is the single formatting authority — `detekt-formatting` is
  intentionally NOT enabled (would run the same rules twice). ktlint is a **hard CI gate**.
- **detekt** (`dev.detekt`, 2.0 alpha) owns **code structure/smells**: complexity, return count, magic
  numbers, generic catches. Shared config in `config/detekt/detekt.yml` (`buildUponDefaultConfig = true`),
  applied to every module by the `spovishun.kotlin-common` convention plugin. Pre-existing findings are
  captured **per module** in `<module>/detekt-baseline.xml`; new code is held to the standard.
  detekt runs **non-blocking** in CI (`continue-on-error`) while only a 2.0 alpha supports Kotlin 2.3 /
  Gradle 9 — the stable 1.23.x line is incompatible. Promote to a hard gate once detekt 2.0 is stable.

Workflow: run `./gradlew ktlintFormat` before committing; regenerate a module's baseline with
`./gradlew :<module>:detektBaseline` only when intentionally accepting new debt (review the diff).

### Pre-commit hook (ktlint)
A version-controlled hook at `.githooks/pre-commit` runs ktlint over the **staged** Kotlin files on
every commit: it auto-fixes what it can and re-stages, and **blocks the commit** (printing each
problem as `file:line`) on anything ktlint cannot fix automatically (e.g. wildcard imports).
Enable it once per clone:
```bash
git config core.hooksPath .githooks
```
It only formats staged content (unstaged changes are stashed during the run), so partial commits are
safe. The hook passes the staged files via `-PinternalKtlintGitFilter`; the `spovishun.kotlin-common`
convention plugin reads that property and narrows each module's ktlint to just the staged files it owns.
The `multiline-expression-wrapping` rule is disabled in `.editorconfig` (keeps `val x = call(…)`
on one line); all other `ktlint_official` rules apply.

## Source Structure
Gradle multi-module build (`settings.gradle.kts` includes `:common :domain :data :bot :admin-api :app`).
Shared config lives in `buildSrc` convention plugins (`spovishun.kotlin-common`,
`spovishun.kotlin-library`); the root `build.gradle.kts` is a pure aggregator (single-sources `version`,
lints its own scripts). Each module owns its `detekt-baseline.xml` and a `CLAUDE.md`.

```
:common  — pure Kotlin, framework-free: result/ (ResultContainer), exception/, extension/, util/
:domain  — by bounded context: bot/ (model/, repository/ interfaces, service/, cache/, config/) +
           admin/ (model/, repository/ — ServerHealth)  (depends on :common)
:data    — shared db/ (DatabaseFactory, DataSourceFactory, DatabaseConfig, ExposedExtensions) +
           by context: bot/ (repository/ impls, table/, mapper/, releasenotes/) + admin/ (repository/ —
           ServerHealth) + tools/ (MigrationGenerator)  (depends on :domain, :common)
:bot     — presentation/: bot/ (TelegramBot, MessageHandler, commands/), controller/, scheduler/, util/
           (depends on :domain, :common — not :data)
:admin-api — embedded Ktor (CIO) read-only observability API: admin/ (config/, dto/, docker/, auth/,
           server/); started from :app, bearer-auth, /api/v1  (depends on :domain, :data, :common)
:app     — composition root: Main.kt, Application.kt (initializeKoin()), config/ (AppConfig),
           di/ (Koin modules); owns the test/integrationTest/e2eTest source sets  (depends on all)
```

## Layer Rules
Dependency direction: `:bot → :domain ← :data`; `:common` ← all modules; `:app` wires everything.
The build enforces this — `:domain` has no dependency on `:data`/`:bot`, `:bot` has none on `:data`.
See per-module `CLAUDE.md` files (`common/`, `domain/`, `data/`, `bot/`, `app/`) for details and examples.

## Key Patterns

**ResultContainer** — own sealed class: `Success<T>(val data: T)` / `Failure(val exception: BaseException)`.
Not related to Kotlin's `Result`. Services and repository interfaces return it.
Chain with `.flatMap {}`, resolve with `.fold(onSuccess = {}, onFailure = {})`.
Wrap DB calls with `ResultContainer.catching { }`.

**DB access** — always `safeDbQuery { }` (wraps `dbQuery {}` + `ResultContainer.catching`), never bare `transaction {}` or `ResultContainer.catching { dbQuery { } }` manually.
`safeDbQuery` and `safeDbTransaction` live in `:data` `data/db/DatabaseFactory.kt`. Only `DatabaseFactory.kt` may use `Dispatchers.IO`.

**Command flow** — `Command` parses args → calls `Controller` → handles `CommandResponse` via `when` → sends to Telegram.
Controllers return `CommandResponse` (never raw strings). Commands own emoji prefixes and final text assembly.
Never call a `Service` directly from a `Command`.

**Role checks** — `MemberService.hasAdminAccess()` / `hasModeratorAccess()` query the DB.
`BotAdminUtils` (`:bot` `presentation/util/`) queries Telegram API only to derive initial role on first registration.

**Profile DI** — single `repositoryModule` in `:app` `di/RepositoryModule.kt` binds all 5 repositories to `:data` `*RepositoryImpl` for both profiles. `PROFILE` controls the DB connection string only (local PostgreSQL for dev, self-hosted PostgreSQL 16 in docker-compose for prod). All bindings use the interface type: `single<MemberRepository> { ... }`.

## Testing
All test source sets live in `:app` (`test`, `integrationTest`, `e2eTest`); `:data` additionally
unit-tests repositories against H2.
- **Unit** — `mockk<*Repository>()` for Services; `mockk<*Service>()` for Controllers.
  Use `runTest {}`, `coEvery`/`coVerify`, `clearAllMocks()` in `@BeforeTest`. `:data` repository unit
  tests run against H2 (`H2TestDatabaseFactory`, PostgreSQL-compatibility mode) — there are no MockImpl repos.
- **Integration** — extend `BaseIntegrationTest`: real services/commands over a **real PostgreSQL**;
  only `Bot` and `BotAdminUtils` are mocked. Reads `.env.e2e`; skips via `assumeTrue` when `E2E_DATABASE_URL` is unset.
- **e2e** — real Telegram API + real PostgreSQL DB; requires `TEST_BOT_TOKEN`, `TEST_HELPER_BOT_TOKEN`, `TEST_CHAT_ID`, `TEST_ADMINS`, `E2E_DATABASE_URL`.
- Do NOT unit test: Koin modules, `TelegramBot`, `MessageHandler`, `DatabaseFactory`.

## Skills Source (generated — do not hand-edit)
The contents of `.claude/` (skills, agents, rules, hooks, `_templates/`, `scripts/notion/`,
`settings.json`) are **generated** by the [`spovishun-skills`](https://www.npmjs.com/package/spovishun-skills)
plugin (dogfooding, spovishun-93). Do not hand-edit generated artifacts — they are overwritten on re-install.

- **Config:** `spovishun-skills.config.yaml` (root, **gitignored** — carries real Notion IDs).
  A sanitized template is committed as `spovishun-skills.config.example.yaml`.
  Secrets stay in `.env`; the config only names the env var (`notion.token_env: NOTION_TOKEN`).
- **Task board:** automation targets **Board v2 (Scrum)** — the "Tasks (v2)" DB. The active-task
  picker filters to the Sprint stage (`notion.picker.stage_filter: "Sprint"`); `create-task.js`
  defaults new tasks to `Stage: Backlog`. The board DB id lives in config (`notion.database_id`);
  `.claude/scripts/notion/lib/constants.js` resolves it at runtime (env var → config, not hard-coded).
- **Install / sync:** `npm install` (pulls `spovishun-skills@^1.4.0`) then
  `npx spovishun-skills install --target=claude` (or `npx spovishun-skills sync` to re-apply with the
  existing config + lockfile). State is tracked in `spovishun-skills.lock.yaml` (committed).
- **Validate:** export `NOTION_TOKEN` from `.env`, then `npx spovishun-skills doctor` → expect 0 errors.
- **Project-owned (NOT plugin-managed), survive re-installs:**
  - `.claude/rules/kotlin/spovishun-architecture.md` — Spovishun concretions (`ResultContainer`,
    `safeDbQuery`/Exposed, Koin) that the generic installed `kotlin-style.md` omits.
  - `.claude/skills/release/` + `.claude/skills/hotfix/` — the GitFlow release/hotfix automation
    (`/release <version>`, `/hotfix <version>`, spovishun-80). No `x-spovishun` frontmatter key,
    not in the lockfile — the plugin never generates, overwrites, or prunes them.
  - `.claude/scripts/notion/tests/` + `TEST-RESULTS.md` — the Notion CLI test suite (migrated from the
    old root `scripts/notion/`). The scripts themselves are now plugin-managed; only these tests are
    project-owned. Run from repo root with a glob — `node --test "**/.claude/scripts/notion/tests/**/*.test.js"`
    (a bare directory arg fails on Node ≥ 22, which tries to load the dir as a module). Integration
    tests skip themselves unless `NOTION_TOKEN` is set.
  - Per-module `common|domain|data|bot|app/CLAUDE.md` (at each module root) and gitignored local state
    (`settings.local.json`, `session-state.json`, learnings queue, `.claude/tmp/`).

## Agent Workflow
- `kotlin-reviewer` — after implementing a feature (reviews Kotlin code quality, architecture, patterns, coroutines)
- `database-reviewer` — after adding a table or migration (reviews DB schema and Exposed usage)
- `doc-updater` — after architectural changes (updates Notion documentation)
- `skill-security-auditor` — before adding a new skill (quality gate: frontmatter, triggers, scope, error handling)
- `code-reviewer` — for PR review with Kotlin-specific checks and verdict section

## Documentation Sync
- `update-doc-full` — range-based Notion doc audit; invoke via `/update-doc-full [range]` (e.g. `1m`); default range `2w`; delegates to `doc-updater` agent, batch-confirms via plannotator, applies via Notion MCP.
- Feature-specific docs (user-facing behavior, commands, versions) → Features category (`notion.so/35f3462f…`). Architecture and Database categories document internal patterns and schema.

## When to use scripts vs MCP (Notion)

| Use case | Preferred tool |
|---|---|
| Board overview | `node .claude/scripts/notion/get-board.js` (supports `--epic <name|id>`) |
| Task by number or pageId | `node .claude/scripts/notion/get-task.js spovishun-<N> \| <pageId>` (number must be the `spovishun-<N>` form, not a bare `<N>`; `--format json\|md`; includes `epic` + `blockedBy`) |
| CLAUDE.md page | `node .claude/scripts/notion/get-claude-md.js` |
| Create task | `node .claude/scripts/notion/create-task.js` (accepts `epicId`, `blockedBy`) or MCP `notion-create-pages` |
| List epics | `node .claude/scripts/notion/list-epics.js` |
| Create epic | `node .claude/scripts/notion/create-epic.js` or skill `newepic` |
| Update status | `node .claude/scripts/notion/update-status.js` or MCP `notion-update-page` |
| Semantic search across arbitrary Notion content | MCP `notion-search` (not replicable via scripts) |

## Task Status Workflow
When you **begin implementing** a board task — at Plan Mode entry, or before the first edit if no
plan step — set its Notion Status to `In progress`:
```bash
node .claude/scripts/notion/update-status.js <task-id> "In progress"
```
This applies even when the task was picked manually (not via a skill). Honor the **single
In-progress** rule — only one task in progress at a time. Move to `Done` only after the PR merges.
On any Notion API failure: log the intended change in chat and continue — never block the actual code work.

## Idea Planning Pipeline
Use these skills to go from a raw idea to implementable tasks:
- `idea-brainstormer` — structures a raw idea into a problem brief (problem statement, scope, risks, feasibility)
- `solution-designer` — compares 2–3 implementation approaches within existing architecture; produces a Solution Decision
- `task-decomposer` — breaks the chosen solution into atomic Notion-compatible tasks with DoD and AI prompts
- `newepic` — creates an Epic page (multi-task initiative). Required by `task-decomposer` when a solution produces 3+ tasks.

Pipeline flow: `idea-brainstormer` → `solution-designer` → `task-decomposer` (creates Epic via `newepic`) → `newtask` / `notion-task-to-code`
Each skill is standalone — invoke at any stage.

### Epics & task relations
The task board has two relation properties:
- `Epic` — links a task to its parent initiative in the Epics database (`d0c0020049f74b0589979065d8cfe7d3`). Use it to group, filter, and roll up tasks.
- `Blocked by` / `Blocks` — self-relation expressing task dependencies inside the board.

Epics live as records of the Epics inline DB on the Documentation page `3633462f68a981098385fa260e9ce132`. Create one whenever an initiative spans 3+ tasks; otherwise omit.

Rules in `.claude/rules/` are always active — they load automatically, no explicit invocation needed.

## Migrations
Files in `:data` `src/main/resources/db/migration/postgresql/` — both dev and prod use Flyway against PostgreSQL.
Run `./gradlew generateMigration`, review SQL, commit `Table` object + migration file together.
Never edit a migration that has been applied to any database.

## Branch Convention
`feature/spovishun-{N}-short-description` — branch from `develop`; `main` is production.
