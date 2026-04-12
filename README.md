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
| Database (dev) | SQLite |
| Database (prod) | PostgreSQL |
| Config | dotenv-kotlin |
| Logging | SLF4J + Logback |

## Project Structure
```
src/main/kotlin/
├── config/             # AppConfig — dotenv-based configuration
├── data/
│   ├── db/
│   │   ├── table/      # Exposed Table objects
│   │   ├── repository/ # Repository implementations
│   │   ├── DatabaseFactory.kt    # DB init + Flyway migrations
│   │   └── DataSourceFactory.kt  # HikariCP datasource factory
│   ├── mapper/         # ResultRow → domain model mappers
│   └── memory/         # In-memory repositories (dev)
├── di/                 # Koin modules
├── domain/
│   ├── BotAdminUtils.kt        # Telegram API admin check (used during registration)
│   ├── model/                  # Pure Kotlin data classes (Member, Group, MemberRole)
│   ├── repository/             # Repository interfaces
│   └── service/                # Business logic (MemberService, GroupService, AutoRegisterService)
├── presentation/
│   ├── bot/                    # Telegram bot + command handlers
│   └── controller/             # Command business logic (GroupController, MembersController)
src/main/resources/
└── db/migration/
    ├── V1__init_schema.sql       # PostgreSQL (prod)
    └── sqlite/
        └── V1__init_schema.sql  # SQLite (dev)
```

## Running
```bash
cp .env.example .env   # fill in your values

./gradlew runDev    # dev profile — in-memory repositories
./gradlew runProd   # prod profile — PostgreSQL + Flyway
```

## Database Migrations

Migrations run automatically on startup (prod only) via Flyway.

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

> ⚠️ Never edit a migration file after it has been applied to any database.

## AI Development (Claude Code)

This project uses [Claude Code](https://claude.ai/code) as the primary AI development agent.

```bash
claude   # launch Claude Code in the project directory
```

The `CLAUDE.md` file in the root provides Claude with full context: architecture, layer rules, naming conventions, commit format, and common task checklists. No additional prompting is needed for standard development tasks.

## Bot Commands

| Команда | Опис |
|---|---|
| `/start` | Реєстрація та привітання |
| `/register` | Ручна реєстрація |
| `/all [текст]` | Тегнути всіх учасників |
| `/ping <група> [текст]` | Тегнути всіх учасників групи |
| `/groups` | Список усіх груп |
| `/members` | Список усіх учасників |
| `/newgroup <назва>` | Створити групу *(адмін)* |
| `/delgroup <назва>` | Видалити групу *(адмін)* |
| `/addtogroup <група> @user` | Додати до групи *(адмін)* |
| `/removefromgroup <група> @user` | Видалити з групи *(адмін)* |
| `/grantrole <роль> @user` | Призначити роль учаснику *(тільки ADMIN)* |

## Environment Variables

| Variable | Example |
|---|---|
| `TELEGRAM_BOT_TOKEN` | `123456:ABC-DEF...` |
| `ADMINS` | `123456789,987654321` |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/spovishun` |
| `DATABASE_DRIVER` | `org.postgresql.Driver` |
| `DATABASE_USERNAME` | `postgres` |
| `DATABASE_PASSWORD` | `secret` |
| `PROFILE` | `dev` або `prod` |