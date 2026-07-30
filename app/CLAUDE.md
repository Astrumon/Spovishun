# :app

Composition root and application entry point. The only module that depends on all others
(`:common`, `:domain`, `:data`, `:bot`) and wires them together. Applies the `application` plugin
(dist name `spovishun`, `mainClass = com.ua.astrumon.MainKt`).

Packages: `config/` (`AppConfig` — env bindings via dotenv), `di/` (Koin modules).
Top level: `Main.kt` (entry point), `Application.kt` (`initializeKoin()`, reusable in tests).

## Koin modules (all live here)
- `ConfigModule` — `AppConfig` + the scheduler coroutine infrastructure: `CoroutineDispatcher`,
  `CoroutineExceptionHandler`, the two qualified `CoroutineScope`s and their `internal object`
  qualifier markers (`BirthdaySchedulerScope`, `ReleaseAnnouncerScope`)
- `RepositoryModule` — binds each `*Repository` interface to its `:data` `*RepositoryImpl`
- `ServiceModule` — domain services
- `PresentationModule` — controllers, commands (`bind BotCommand::class`), bot, schedulers
- `AdminApiDiModule` — `AdminApiConfig`, `DockerApiClient`, `AdminApiServer`. Named `…DiModule` to stay
  distinct from the Ktor routing module `Application.adminApiModule` in `:admin-api` (spovishun-156)

`AppModules.kt` collects the five into `appModules` — the single list `Application.initializeKoin()`
starts Koin with and `KoinModuleGraphTest` verifies. Register a new module there, not in `Application.kt`,
so it cannot reach production wiring unverified. All declarations are `internal`; `:app` is the top of
the dependency graph. Bind by interface; constructor injection only. `PROFILE` selects the DB
connection string (local PostgreSQL for dev, self-hosted PostgreSQL for prod) — not which
implementations are bound.

## Shutdown
`Application.run()` registers a JVM shutdown hook — **after** `initializeKoin()`, since `shutdown()`
resolves its dependencies from Koin. It runs outside-in: `AdminApiServer.stop()` (grace period) →
`stopKoin()` → `DatabaseFactory.close()` (Hikari pool, last). Koin-owned resources release themselves
through `onClose` on their binding — the two scheduler scopes are cancelled and `DockerApiClient` closes
its `HttpClient`. Declare cleanup with the binding (`single { … }.onClose { … }`), not in the hook; only
the DB pool, which is initialized outside Koin, is closed explicitly.

Scope cancellation is cooperative and non-blocking: it stops new scheduler work, it does not drain
queries already running on `Dispatchers.IO`.

## Test source sets (owned by this module)
- `test` (`./gradlew test`) — unit tests with MockK; H2 available for any DB-touching helpers.
- `integrationTest` (`./gradlew integrationTest`) — real services/commands over a real PostgreSQL;
  only `Bot` and `BotAdminUtils` are mocked. Reads `.env.e2e`; skips when `E2E_DATABASE_URL` is unset.
- `e2eTest` (`./gradlew e2eTest`) — real Telegram API + real PostgreSQL. Requires the `TEST_*` /
  `E2E_DATABASE_URL` env vars; skips otherwise. Note that unsetting them is not enough locally —
  `E2EConfig` falls back to the repo-root `.env` and `E2EDbConfig` to `.env.e2e`.

### What belongs in e2e (spovishun-160)
**e2e is only for assertions that need Telegram's own answer.** Anything provable over real
PostgreSQL with a mocked `Bot` belongs in `integrationTest`, which already covers every command and
role gate. Concretely, e2e owns: HTML parse mode, the 4096-character limit, inline-keyboard
acceptance, mention entities, and `getChatMember`/`getChatAdministrators` role derivation.

`BaseE2ETest` makes that possible by wrapping the real `Bot` in a MockK spy that records the
`TelegramBotResult<Message>` of every send — Telegram's view of the delivered message. It stubs
nothing; the calls are real. A helper bot cannot be used to read replies back: Telegram never
delivers one bot's messages to another, which is why the old `TelegramHelperBot` polling API was
dead code.

Two consequences to respect when adding tests:
- **Every `dispatch()` posts to a real chat.** Telegram allows roughly 20 messages per minute per
  chat; the suite sits at ~11 per run. The harness honours `retry_after` on a 429, but do not treat
  that as headroom — a test that only checks database rows must not live here.
- **`dispatch()` fails on an unregistered command name.** Keep `BaseE2ETest`'s registry in lockstep
  with `di/PresentationModule`. Plain non-command messages are `MessageHandler`'s job and stay in
  `MessageHandlerIntegrationTest`.

Full coverage matrix and rationale: Notion → Documentation → Testing → *E2E Suite Audit & Layer Split
(spovishun-160)* (`notion.so/3ad3462f68a98122b5abfad51a4fcefb`).

Do NOT unit test Koin modules, `TelegramBot`, `MessageHandler`, or `DatabaseFactory`.
Exception: `KoinModuleGraphTest` runs `koin-test`'s `verify()` over all five modules. That is a static
reflection check of the graph (nothing is instantiated), not a test of module logic — it exists so a
forgotten binding fails in CI instead of on production startup (spovishun-156).

## Logging
`logback.xml` and the logging backend live with this entry-point module.
