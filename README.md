# SpovishunTelegramBotV2

A Kotlin-based Telegram bot built with Clean Architecture, Koin DI, Exposed ORM, and Flyway database migrations.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.3.0 (JVM 21) |
| Build | Gradle Kotlin DSL + Version Catalog |
| DI | Koin 3.x |
| ORM | Exposed 0.55.0 |
| Migrations | Flyway 10.x |
| Database (dev) | PostgreSQL (local) |
| Database (prod) | PostgreSQL 16 (self-hosted, Docker on Oracle Cloud VM) |
| Config | dotenv-kotlin |
| Logging | SLF4J + Logback |

## Project Structure
```
src/main/kotlin/
├── Application.kt          # Koin init + bot startup
├── config/                 # AppConfig — dotenv-based env var bindings
├── common/                 # ResultContainer, exceptions, extensions, logging
├── domain/
│   ├── cache/              # In-memory cache strategies
│   ├── model/              # Pure Kotlin data classes (Member, Group, MemberRole, MemberChat)
│   ├── repository/         # Repository interfaces (5 total)
│   └── service/            # Business logic (MemberService, GroupService, AutoRegisterService…)
├── data/
│   ├── db/
│   │   ├── table/          # Exposed Table objects
│   │   ├── repository/     # DB repository implementations
│   │   ├── DatabaseFactory.kt   # DB init + Flyway migrations
│   │   └── DataSourceFactory.kt # HikariCP datasource
│   ├── mapper/             # ResultRow → domain model mappers
│   └── memory/             # MockImpl repositories (integration tests only)
├── di/                     # Koin modules
└── presentation/
    ├── CommandResponse.kt  # Sealed class: Success / AccessDenied / NotFound / Error
    ├── bot/                # TelegramBot, MessageHandler, commands/
    ├── controller/         # Command controllers (return CommandResponse)
    └── util/               # BotAdminUtils (Telegram API admin check)
src/main/resources/
└── db/migration/postgresql/
    ├── V1__init_schema.sql
    ├── ...
    └── V8__normalize_members_chats.sql
```

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
# Add:
0 3 * * * cd /home/ubuntu/Spovishun && ./scripts/backup.sh >> backups/cron.log 2>&1
```

Backups older than 14 days are removed automatically.

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
./gradlew test              # unit tests
./gradlew integrationTest   # integration tests (real MockImpl repos)
./gradlew e2eTest           # e2e tests (real Telegram API, skips if env vars unset)
```

## Database Migrations

Migrations run automatically on startup for both dev and prod via Flyway.

### Adding a migration

1. Update the `Table` object in `data/db/table/`
2. Generate the SQL:
```bash
./gradlew generateMigration
# → Enter migration description: add_member_lastname
# → ✅ Created: V2__add_member_lastname.sql
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

## Environment Variables

| Variable | Example |
|---|---|
| `TELEGRAM_BOT_TOKEN` | `123456:ABC-DEF...` |
| `PROFILE` | `dev` or `prod` |
| `DEV_DATABASE_URL` | `jdbc:postgresql://localhost:5432/spovishun` |
| `DEV_DATABASE_DRIVER` | `org.postgresql.Driver` |
| `DEV_DATABASE_USERNAME` | `postgres` |
| `DEV_DATABASE_PASSWORD` | `secret` |
| `PROD_DATABASE_URL` | `jdbc:postgresql://postgres:5432/spovishun_prod` |
| `PROD_DATABASE_DRIVER` | `org.postgresql.Driver` |
| `PROD_DATABASE_USERNAME` | `postgres` |
| `PROD_DATABASE_PASSWORD` | `secret` |
