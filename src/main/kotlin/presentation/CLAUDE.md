# presentation/

Contains: `bot/` (TelegramBot, MessageHandler, `commands/`), `controller/`, `util/` (BotAdminUtils).

## Forbidden imports
- Exposed / JDBC (`org.jetbrains.exposed.*`) — no DB access in this layer
- Any `domain/service/` import directly in a `Command` class

## Command flow
```
Command → Controller → returns CommandResponse → Command formats + sends to Telegram
```
1. **Command** — parse args from `Update`, call one `Controller` method, convert result via `toText()`, send to Telegram.
2. **Controller** — call `Service`(s), apply role checks, return `CommandResponse`. Never returns Telegram types or raw strings.
3. **BotAdminUtils** — query Telegram API only to derive initial role for a new member during registration.

`CommandResponse` sealed class (`presentation/CommandResponse.kt`):
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
Runs `CoroutineScope(SupervisorJob())` — one failing command never kills the bot.
All handlers are `suspend fun`.

## MessageHandler
Routes updates to commands via `when`. No logic beyond routing.
Do NOT unit test `MessageHandler` or `TelegramBot`.

## Adding a new command
1. Create `bot/commands/{Name}Command.kt` implementing `BotCommand` (`name`, `execute`)
2. Create `controller/{Entity}Controller.kt` (if new domain area)
3. Register in `di/PresentationModule.kt`: `single { NameCommand(get()) } bind BotCommand::class`
4. Done — `TelegramBot` picks it up automatically via `CommandRegistry`
