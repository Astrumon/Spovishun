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
| Database (prod) | PostgreSQL (Neon) |
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
./gradlew runProd   # PROFILE=prod — Neon PostgreSQL + Flyway
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
| `PROD_DATABASE_URL` | `jdbc:postgresql://...neon.tech/spovishun` |
| `PROD_DATABASE_DRIVER` | `org.postgresql.Driver` |
| `PROD_DATABASE_USERNAME` | `postgres` |
| `PROD_DATABASE_PASSWORD` | `secret` |
