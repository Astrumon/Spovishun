# Spovishun

A Kotlin-based Telegram bot built with Clean Architecture, Koin DI, Exposed ORM, and Flyway database migrations.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.3.0 (JVM 21) |
| Build | Gradle Kotlin DSL + Version Catalog |
| DI | Koin 4.x |
| ORM | Exposed 0.55.0 |
| Migrations | Flyway 12.x |
| Database (dev) | PostgreSQL (local) |
| Database (prod) | PostgreSQL 16 (self-hosted, Docker on Oracle Cloud VM) |
| Telegram | kotlin-telegram-bot 6.x |
| HTTP | Ktor client + Retrofit |
| Config | dotenv-kotlin |
| Logging | SLF4J + Logback |

> Architecture follows Clean Architecture (`presentation → domain ← data`). See `CLAUDE.md` and the per-layer `CLAUDE.md` files in `domain/`, `data/`, and `presentation/` for the current package layout and conventions.

## Running
```bash
cp .env.example .env   # fill in your values

./gradlew runDev    # PROFILE=dev  — local PostgreSQL + Flyway
./gradlew runProd   # PROFILE=prod — cloud PostgreSQL + Flyway
```

## Deployment

Production deploys automatically on merge to `main` via `.github/workflows/deploy.yml`.
The workflow builds a Docker image, pushes to `ghcr.io`, then SSHes to the Oracle Cloud VM and runs:
```bash
docker compose --profile prod pull bot
docker compose --profile prod up -d
```

### First-time VM setup
```bash
# 1. Clone repo and create .env
git clone https://github.com/Astrumon/spovishun.git ~/Spovishun
cd ~/Spovishun
cp .env.example .env
nano .env  # set TELEGRAM_BOT_TOKEN, POSTGRES_PASSWORD, PROD_DATABASE_PASSWORD, etc.

# 2. Start the full stack
docker compose --profile prod up -d

# 3. Verify both services are healthy
docker compose ps
docker compose logs bot --tail 50
```

### Private admin access (Tailscale + docker-socket-proxy)

The prod stack ships a read-only Docker API gateway (`docker-socket-proxy`, `prod` profile) so the
bot / future admin-api never touches the raw `docker.sock`. It exposes a GET-only subset
(`CONTAINERS`, `INFO`), mounts the socket read-only, and is reachable **only** on the internal
`proxy-net` docker network — no host port is published.

Admin access to the VM goes over **Tailscale**, not a public port:

```bash
# 1. Install Tailscale on the VM and join the tailnet
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up        # authenticate, then record the assigned tailnet IP (100.x.y.z)

# 2. Bring up the proxy (part of the prod profile)
docker compose --profile prod up -d docker-socket-proxy

# 3. Sanity-check permissions from the bot container
docker compose exec bot sh -c 'curl -s http://docker-socket-proxy:2375/containers/json'      # 200 + JSON
docker compose exec bot sh -c 'curl -s -o /dev/null -w "%{http_code}" -XPOST http://docker-socket-proxy:2375/containers/create'  # 403
```

Record the tailnet IP in your ops notes (it is not a secret and is not read from `.env`). No new
public port should appear — verify with an external scan of the VM's public IP.

## Backups

`scripts/backup.sh` creates a compressed daily backup of the production database.

```bash
# Manual run (from ~/Spovishun on the VM)
chmod +x scripts/backup.sh
./scripts/backup.sh

# Output: backups/spovishun_YYYYMMDD_HHMMSS.dump.gz
# Log:    backups/backup.log
```

### Automated daily backup (cron)
```bash
crontab -e
# Add (adjust the path to your clone location):
0 3 * * * cd ~/Spovishun && ./scripts/backup.sh >> backups/cron.log 2>&1
```

Backups older than 14 days are removed automatically.

### Pull backups to a local machine

`scripts/pull-backup.sh` downloads the latest dump from the VM over SSH for off-site storage.

```bash
./scripts/pull-backup.sh
```

### Restore from backup
```bash
# 1. Copy dump into the container
docker cp backups/spovishun_YYYYMMDD_HHMMSS.dump.gz spovishun-db:/tmp/restore.dump.gz

# 2. Decompress and restore
docker exec spovishun-db bash -c "gunzip -c /tmp/restore.dump.gz > /tmp/restore.dump"
docker exec spovishun-db pg_restore \
  -U postgres -d spovishun_prod \
  --no-owner --no-acl --clean --if-exists \
  /tmp/restore.dump

# 3. Cleanup
docker exec spovishun-db rm /tmp/restore.dump /tmp/restore.dump.gz
```

## Testing
```bash
./gradlew test              # unit tests (mocked dependencies)
./gradlew integrationTest   # in-process tests against a real DB (needs E2E_DATABASE_URL, else skipped)
./gradlew e2eTest           # e2e tests (real Telegram API, skips if env vars unset)
```

Integration and e2e tests require extra environment variables — see [Environment Variables](#environment-variables).

## Database Migrations

Migrations run automatically on startup for both dev and prod via Flyway.

### Adding a migration

1. Update the `Table` object in `data/db/table/`
2. Generate the SQL:
```bash
./gradlew generateMigration
# → Enter migration description: add_member_lastname
# → ✅ Created: V12__add_member_lastname.sql   # next free version
```
3. Review the generated file
4. Commit the `Table` file and migration script together

> Never edit a migration file after it has been applied to any database.

## AI Development (Claude Code)

This project uses [Claude Code](https://claude.ai/code) as the primary AI development agent.

```bash
claude   # launch Claude Code in the project directory
```

`CLAUDE.md` in the root provides full context: architecture, layer rules, naming conventions, commit format, and task checklists. `.claude/` contains hooks, skills, agents, and rules that automate Notion sync, code review, and task management.

## Bot Commands

| Command | Description |
|---|---|
| `/start` | Registration and welcome message |
| `/register` | Manual registration |
| `/all [text]` | Ping all members |
| `/ping <group> [text]` | Ping all members of a group |
| `/groups` | List all groups |
| `/members` | List all members |
| `/newgroup <name>` | Create a group *(admin)* |
| `/delgroup <name>` | Delete a group *(admin)* |
| `/addtogroup <group> @user` | Add user to group *(admin)* |
| `/removefromgroup <group> @user` | Remove user from group *(admin)* |
| `/grantrole <role> @user` | Assign role to member *(admin only)* |
| `/birthday <date>` | Set your own birthday; `off` clears it; `<date> @user` sets another member's *(admin/moderator)* |
| `/random [group]` | Pick a random member from the chat, or from a named group |
| `/whatsnew` | Show the latest release notes; `$h` full history; `$on`/`$off` toggle per-chat broadcasts *(admin)* |

On startup with a new version, the bot auto-broadcasts the release notes to every chat that has announcements enabled.

## Environment Variables

Copy `.env.example` to `.env` and fill in the values. The variables below are grouped by purpose.

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

### Tests (integration & e2e — optional)
| Variable | Example | Notes |
|---|---|---|
| `E2E_DATABASE_URL` | `jdbc:postgresql://localhost:5432/spovishun_e2e` | Real DB for `integrationTest` / `e2eTest`; unset = those tests skip |
| `E2E_DATABASE_USERNAME` | `postgres` | |
| `E2E_DATABASE_PASSWORD` | `secret` | |
| `TEST_BOT_TOKEN` | `123456:ABC...` | e2e: token of the bot under test |
| `TEST_HELPER_BOT_TOKEN` | `654321:ZYX...` | e2e: second bot used to drive interactions |
| `TEST_CHAT_ID` | `-100123` | e2e: chat the tests run in |
| `TEST_ADMINS` | `111,222` | e2e: admin user IDs |

### Tooling (optional)
| Variable | Example | Notes |
|---|---|---|
| `NOTION_SKILLS_TOKEN` | `ntn_...` | Used by the Claude Code skills→Notion sync hook only; not needed to run the bot |
| `DOCKER_API_URL` | `http://docker-socket-proxy:2375` | Read-only Docker API via the socket proxy (prod, internal network). Defaults in compose; consumed by the future admin-api |
