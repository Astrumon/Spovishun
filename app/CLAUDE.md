# :app

Composition root and application entry point. The only module that depends on all others
(`:common`, `:domain`, `:data`, `:bot`) and wires them together. Applies the `application` plugin
(dist name `spovishun`, `mainClass = com.ua.astrumon.MainKt`).

Packages: `config/` (`AppConfig` — env bindings via dotenv), `di/` (Koin modules).
Top level: `Main.kt` (entry point), `Application.kt` (`initializeKoin()`, reusable in tests).

## Koin modules (all live here)
- `ConfigModule` — `AppConfig` + coroutine scopes/dispatchers/handlers
- `RepositoryModule` — binds each `*Repository` interface to its `:data` `*RepositoryImpl`
- `ServiceModule` — domain services
- `PresentationModule` — controllers, commands (`bind BotCommand::class`), bot, schedulers

Bind by interface; constructor injection only. `PROFILE` selects the DB connection string
(local PostgreSQL for dev, self-hosted PostgreSQL for prod) — not which implementations are bound.

## Test source sets (owned by this module)
- `test` (`./gradlew test`) — unit tests with MockK; H2 available for any DB-touching helpers.
- `integrationTest` (`./gradlew integrationTest`) — real services/commands over a real PostgreSQL;
  only `Bot` and `BotAdminUtils` are mocked. Reads `.env.e2e`; skips when `E2E_DATABASE_URL` is unset.
- `e2eTest` (`./gradlew e2eTest`) — real Telegram API + real PostgreSQL via `TelegramHelperBot`
  (Ktor client). Requires the `TEST_*` / `E2E_DATABASE_URL` env vars; skips otherwise.

Do NOT unit test Koin modules, `TelegramBot`, `MessageHandler`, or `DatabaseFactory`.

## Logging
`logback.xml` and the logging backend live with this entry-point module.
