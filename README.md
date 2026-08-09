# Spovishun

Kotlin Telegram bot built with Clean Architecture across six Gradle modules — Koin DI, Exposed ORM,
Flyway migrations, and an embedded read-only observability API.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.3.0 (JVM 21) |
| Build | Gradle Kotlin DSL + Version Catalog + `buildSrc` convention plugins |
| DI | Koin 4.1.1 |
| ORM | Exposed 0.55.0 |
| Migrations | Flyway 12.1.0 |
| Database | PostgreSQL — local for dev, self-hosted `postgres:16-alpine` (Docker on an Oracle Cloud VM) for prod; driver 42.7.7 |
| Connection pool | HikariCP 5.1.0 |
| Telegram | kotlin-telegram-bot 6.3.0 |
| HTTP | Ktor 3.0.3 — CIO **server** for `:admin-api`, client for the Docker API and e2e tests |
| Serialization | kotlinx.serialization |
| Config | dotenv-kotlin |
| Logging | SLF4J + Logback |
| Lint / static analysis | ktlint 1.5.0 (plugin 14.2.0), detekt 2.0.0-alpha.3 |

Canonical version source: [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Modules

```
:common     pure Kotlin, framework-free — ResultContainer, exceptions, extensions, util
:domain     business logic by bounded context — bot/ + admin/          (depends on :common)
:data       Exposed tables, repositories, DatabaseFactory, Flyway      (depends on :domain, :common)
:bot        Telegram adapter — commands, controllers, schedulers       (depends on :domain, :common)
:admin-api  embedded Ktor (CIO) read-only observability API            (depends on :domain, :data, :common)
:app        composition root — Main.kt, AppConfig, Koin modules        (depends on all)
```

Dependency direction is enforced by the build, not by convention: `:bot → :domain ← :data`,
`:common ← everything`, and `:app` wires it together. `:bot` has no dependency on `:data`.

Each module has its own `CLAUDE.md` with layer rules and examples; the root
[`CLAUDE.md`](CLAUDE.md) holds the project-wide conventions. Deeper architecture write-ups live in
[Notion → Documentation](https://www.notion.so/3193462f68a981b79936e2e45291df85) —
see [Multi-module build](https://www.notion.so/3b73462f68a981b19728fdaa968053fa).

## Running

```bash
cp .env.example .env   # fill in your values

./gradlew runDev    # PROFILE=dev  — local PostgreSQL + Flyway
./gradlew runProd   # PROFILE=prod — prod PostgreSQL + Flyway
```

## Testing

```bash
./gradlew test              # unit tests — every module owns its own src/test
./gradlew integrationTest   # :app — real services/commands over a real PostgreSQL
./gradlew e2eTest           # :app — real Telegram API + real PostgreSQL
```

A unit test lives in the module of the code it covers; `:app` additionally owns the two
cross-module source sets. `integrationTest` and `e2eTest` skip themselves via `assumeTrue` when
their environment variables are unset, so they are safe to run anywhere.

## Linting

```bash
./gradlew ktlintFormat   # auto-fix formatting — run before committing
./gradlew ktlintCheck    # verify formatting — hard CI gate
./gradlew detekt         # static analysis — non-blocking in CI
```

Enable the version-controlled pre-commit hook once per clone; it formats staged Kotlin files and
blocks the commit on anything ktlint cannot fix:

```bash
git config core.hooksPath .githooks
```

## Database Migrations

Migrations run automatically on startup for both profiles via Flyway. The repository is at **V15**.

1. Update the `Table` object in `data/src/main/kotlin/data/bot/table/`
2. Generate the SQL:
```bash
./gradlew generateMigration
# → Enter migration description: add_member_lastname
# → ✅ Created: V16__add_member_lastname.sql   # next free version
```
3. Review the generated file
4. Commit the `Table` file and migration script together

> Never edit a migration file after it has been applied to any database.

## Bot Commands

Roles: **any** · **mod** = admin or moderator · **admin** = admin only.
Commands marked 🔘 open an inline picker when called without arguments.

| Command | Role | Description |
|---|---|---|
| `/start` | any | Welcome message with the full command list; pre-registers the chat's admins |
| `/register [$b DD.MM]` | any | Manual registration; `$b` sets your birthday at the same time |
| `/members` | any | List registered members and their roles |
| `/groups` | any | List the chat's groups |
| `/all [text]` | any | Ping every registered member |
| `/all $ready-on\|$ready-off` | mod | Toggle readiness-poll mode for the chat |
| `/ping <group> [text]` 🔘 | any | Ping every member of a group |
| `/ping <group> $ready-on\|$ready-off` | mod | Toggle readiness-poll mode for one group |
| `/random [group]` 🔘 | any | Pick a random member of the chat or of a group |
| `/birthday DD.MM` · `/birthday off` | any | Set or clear your own birthday |
| `/birthday DD.MM @user` | mod | Set another member's birthday — **date first**, then the username |
| `/whatsnew [$h]` | any | Latest release notes; `$h` shows the full version history |
| `/whatsnew $on\|$off` | admin | Toggle release announcements for this chat |
| `/language` 🔘 | mod | Pick the chat's language (🇺🇦 / 🇬🇧) |
| `/newgroup <name>` 🔘 | mod | Create a group |
| `/delgroup <name>` 🔘 | mod | Delete a group (with confirmation) |
| `/addtogroup <name> @u1, @u2` 🔘 | mod | Add one or more members to a group |
| `/removefromgroup <name> @u1, @u2` 🔘 | mod | Remove one or more members from a group |
| `/editg <name> [$icon=…] [$mark=…] [$name=…]` | mod | Show or change a group's icon, ping mark and name |
| `/grantrole @u1, @u2 member\|moderator\|admin` 🔘 | admin | Assign a role to one or more members |

Passive components: a birthday scheduler greets members daily at 12:00 Europe/Kyiv, and on startup
with a new version the bot broadcasts the release notes to every chat that has announcements
enabled. Messages render in the chat's selected language.

## Admin API

An embedded Ktor (CIO) server runs inside the bot process, disabled by default and enabled with
`ADMIN_API_ENABLED=true`. It is read-only, guarded by a bearer token, and in production reachable
only over Tailscale — no public port is published.

| Endpoint | Description |
|---|---|
| `GET /api/v1/health` | DB connectivity + database size |
| `GET /api/v1/metrics` | Docker host info + per-container CPU/memory |
| `GET /api/v1/containers` | Container list |
| `GET /api/v1/containers/{id}/logs?tail=N` | Log snapshot (default 100 lines) |
| `GET /api/v1/containers/{id}/logs/stream` | Live log tail over SSE |

Base URLs — dev: `http://127.0.0.1:8081/api/v1`, prod: `http://<VM-tailnet-IP>:8081/api/v1`.
Every request needs `Authorization: Bearer <ADMIN_API_TOKEN>`; missing or invalid → `401`.
Details: [Admin API — in-process Ktor server topology](https://www.notion.so/3853462f68a981968d73eeb354c42a12).

## Environment Variables

Copy `.env.example` to `.env` and fill in the values. Test-only variables live in `.env.e2e`
(see `.env.e2e.example`).

### Bot & security
| Variable | Example | Notes |
|---|---|---|
| `TELEGRAM_BOT_TOKEN` | `123456:ABC-DEF...` | Bot token from BotFather |
| `PROFILE` | `dev` or `prod` | Set automatically by `runDev`/`runProd` |
| `EXPECTED_BOT_USERNAME` | `MyBot` | Identity lock — bot's `@username` (no `@`). Empty = check skipped (warns). Required in prod |
| `ALLOWED_CHAT_IDS` | `-100123,-100456` | Comma-separated chat allowlist. Empty = all chats allowed |

### Database
| Variable | Example | Notes |
|---|---|---|
| `DEV_DATABASE_URL` | `jdbc:postgresql://localhost:5432/spovishun_dev` | Overridden to `postgres:5432` under Docker Compose |
| `DEV_DATABASE_DRIVER` | `org.postgresql.Driver` | |
| `DEV_DATABASE_USERNAME` | `postgres` | |
| `DEV_DATABASE_PASSWORD` | `secret` | |
| `DEV_DATABASE_POOL_SIZE` | `10` | HikariCP pool size |
| `PROD_DATABASE_URL` | `jdbc:postgresql://postgres:5432/spovishun_prod` | |
| `PROD_DATABASE_DRIVER` | `org.postgresql.Driver` | |
| `PROD_DATABASE_USERNAME` | `postgres` | |
| `PROD_DATABASE_PASSWORD` | `secret` | |
| `PROD_DATABASE_POOL_SIZE` | `10` | HikariCP pool size |

### Postgres container (Docker Compose)
| Variable | Example | Notes |
|---|---|---|
| `POSTGRES_PASSWORD` | `secret` | Password the `postgres` container starts with (match the active profile's DB password) |
| `POSTGRES_DB` | `spovishun_prod` | DB name created on first container start |
| `POSTGRES_USER` | `postgres` | |

### Admin API
| Variable | Default | Notes |
|---|---|---|
| `ADMIN_API_ENABLED` | `false` | Master switch. When `true`, `ADMIN_API_TOKEN` is required (fail-fast) |
| `ADMIN_API_BIND` | `127.0.0.1` | Interface the server binds **inside** the process/container. `0.0.0.0` in prod compose |
| `ADMIN_API_PORT` | `8081` | Listen port |
| `ADMIN_API_TOKEN` | — | Bearer secret, constant-time compared. Generate with `openssl rand -hex 32`. Never commit it |
| `ADMIN_API_BIND_IP` | `127.0.0.1` | **docker-compose only** — host IP the container port is published on. The VM's tailnet IP in prod; never `0.0.0.0` |
| `DOCKER_API_URL` | `http://docker-socket-proxy:2375` | Read-only Docker Engine API via docker-socket-proxy |

### Tests — `.env.e2e` (optional)
| Variable | Example | Notes |
|---|---|---|
| `E2E_DATABASE_URL` | `jdbc:postgresql://localhost:5432/spovishun_e2e` | Real DB for `integrationTest` / `e2eTest`; unset = those tests skip |
| `E2E_DATABASE_USERNAME` | `postgres` | |
| `E2E_DATABASE_PASSWORD` | `secret` | |
| `TEST_BOT_TOKEN` | `123456:ABC...` | e2e: token of the bot under test |
| `TEST_HELPER_BOT_TOKEN` | `654321:ZYX...` | e2e: supplies a real user id for the synthetic updates |
| `TEST_CHAT_ID` | `-100123` | e2e: chat the tests run in |

### Tooling (optional)
| Variable | Example | Notes |
|---|---|---|
| `NOTION_SKILLS_TOKEN` | `ntn_...` | Used by the Claude Code skills→Notion sync hook only; not needed to run the bot |

## Deployment & Operations

Production deploys automatically on merge to `main` via `.github/workflows/deploy.yml`: it builds a
Docker image, pushes it to `ghcr.io`, then SSHes to the Oracle Cloud VM, syncs the working tree to
`origin/main` and restarts the stack with `docker compose --profile prod up -d`.

Runbooks live in Notion rather than here, so they stay in one place:

- [Deployment Guide — Oracle Cloud VM](https://www.notion.so/3403462f68a9818e9e53e743d9219295) — first-time VM setup, image build/push, update flow, troubleshooting
- [Server Reference — Oracle Cloud VM](https://www.notion.so/35b3462f68a981efb612db7695ad1b8e) — SSH access, required `.env`, GitHub secrets, quick commands
- [Self-Hosted PostgreSQL Setup](https://www.notion.so/3543462f68a9810a9b3bf4756694ff59) — `scripts/backup.sh`, daily cron, retention, restore and validation
- [Tailscale — private access to the prod VM](https://www.notion.so/3853462f68a9814fae0ace9ea7d83a1e)
- [docker-socket-proxy — read-only Docker API](https://www.notion.so/3853462f68a9815c826be66460ba2d72)

## AI Development (Claude Code)

This project uses [Claude Code](https://claude.ai/code) as the primary AI development agent.

```bash
claude   # launch Claude Code in the project directory
```

`CLAUDE.md` in the root provides full context: architecture, layer rules, naming conventions, commit
format, and task checklists. `.claude/` contains hooks, skills, agents, and rules that automate
Notion sync, code review, and task management.
