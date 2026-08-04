# :bot

Presentation module (Telegram). Depends on `:domain` and `:common`. Does NOT depend on `:data` —
repository wiring happens in `:app`.

Packages (under `presentation/`): `bot/` (TelegramBot, commands/, handler/), `controller/`,
`scheduler/`, `util/` (BotAdminUtils).

## Forbidden dependencies
- Exposed / JDBC (`org.jetbrains.exposed.*`) — no DB access in this layer
- Any `:domain` service import directly in a `Command` class (go through a `Controller`)
- A Gradle dependency on `:data`

## Command flow
```
Command → Controller → returns CommandResponse → Command formats + sends to Telegram
```
1. **Command** — parse args from `Update`, call one `Controller` method, convert result via `toText()`, send to Telegram.
2. **Controller** — call `Service`(s), apply role checks, return `CommandResponse`. Never returns Telegram types or raw strings.
3. **BotAdminUtils** — query Telegram API only to derive initial role for a new member during registration.

`CommandResponse` sealed class:
- `Success(message: String)` — formatted HTML body, no emoji prefix
- `AccessDenied(reason: String)` — e.g. `"moderator"` or `"admin"`
- `NotFound(resource: String, identifier: String)`
- `Error(message: String)`

Commands own emoji prefixes and final text assembly. Controllers return body only.

### CommandResponse.toText()

Always use `toText()` — never repeat a `when` block in a command. Pass callbacks only for cases that differ from defaults.

```kotlin
// No custom cases — use defaults
val text = controller.doSomething(chatId, userId, args).toText()

// Success prefix only
val text = controller.register(chatId, userId, args).toText(successPrefix = "✅ ")

// Custom access denied and not found
val text = controller.grantRole(chatId, userId, args).toText(
    successPrefix = "✅ ",
    onAccessDenied = { "🚫 Лише адміни можуть призначати ролі." },
    onNotFound = { "❌ ${it.resource} '${it.identifier}' не знайдено." },
)

// Wrong: service called directly from command
class BadCommand(private val memberService: MemberService) { ... }
```

## Role checks in controllers
Use `MemberService.hasAdminAccess()` / `hasModeratorAccess()` (DB-based) for permission guards.
Use `BotAdminUtils.getMemberRole()` only when deriving the initial role for a new member.

## TelegramBot
Runs on an injected `CoroutineScope` carrying `SupervisorJob` + a scope-level `CoroutineExceptionHandler`
— one failing command never kills the bot. All handlers are `suspend fun`.

## MessageHandler
Routes updates to commands via `when`. No logic beyond routing.
Do NOT unit test `MessageHandler` or `TelegramBot`.

## Chat log context (spovishun-168)
Every log line emitted while handling an update carries the originating chat, so the Spovishun
Admin live-log view can attribute and filter per chat. `presentation/util/ChatLogContext.kt` owns
the mechanics: `withChatLogContext(chatId, chatType) { }` puts the two `ChatLogContext` keys into
the SLF4J MDC **via `MDCContext`**, never via a bare `MDC.put`. That is not a stylistic choice —
the MDC is a thread-local, so a plain put is lost the moment `safeDbQuery` hops to
`Dispatchers.IO`, which is where most interesting lines come from. Passing the map explicitly also
means the coroutines machinery owns cleanup, on completion, cancellation and failure alike.

Three dispatch paths, one wrap each — do not add a fourth without a wrap:
| Path | Wrapped by |
|---|---|
| Commands | `ChatContextCommand`, applied to every entry by `CommandRegistry` — registering a command is enough |
| Text messages | `MessageHandler.handleIncomingMessage` |
| Callback queries | `CallbackRouter.route` |

`scope.launch {}` takes its context from the injected scope, **not** from the caller, so deferred
work would log as `system`. Pass `chatLogContextSnapshot()` to the launch — see
`ReadinessSessionRunner`. Schedulers have no ambient chat: they wrap each per-chat send
individually (`BirthdayGreetingScheduler.sendGreetings`, `ReleaseAnnouncer.sendToAllChats`), and
pass-level lines correctly render as `system`.

An absent key renders as `system` through the encoder default in `app/src/main/resources/logback.xml`
— there is no "system context" API to call. Per `security.md`, these fields carry the chat id and
chat type only; never a username, user id, or message body.

## Adding a new command
1. Create `bot/commands/{Name}Command.kt` implementing `BotCommand` (`name`, `execute`)
2. Create `controller/{Entity}Controller.kt` (if new domain area)
3. Register in `:app` `di/PresentationModule.kt`: `single { NameCommand(get()) } bind BotCommand::class`
4. Done — `TelegramBot` picks it up automatically via `CommandRegistry`
