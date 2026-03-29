# presentation/

Contains: `bot/` (TelegramBot, MessageHandler, `commands/`), `controller/`, `util/` (BotAdminUtils).

## Forbidden imports
- Exposed / JDBC (`org.jetbrains.exposed.*`) — no DB access in this layer
- Any `domain/service/` import directly in a `Command` class

## Command flow
```
Command → Controller → returns CommandResponse → Command formats + sends to Telegram
```
1. **Command** — parse args from `Update`, call one `Controller` method, handle `CommandResponse` via `when`, send to Telegram.
2. **Controller** — call `Service`(s), apply role checks, return `CommandResponse`. Never returns Telegram types or raw strings.
3. **BotAdminUtils** — query Telegram API only to derive initial role for a new member during registration.

`CommandResponse` sealed class (`presentation/CommandResponse.kt`):
- `Success(message: String)` — formatted HTML body, no emoji prefix
- `AccessDenied(reason: String)` — e.g. `"moderator"` or `"admin"`
- `NotFound(resource: String, identifier: String)`
- `Error(message: String)`

Commands own emoji prefixes and final text assembly. Controllers return body only.

### CommandResponse.toText()

For the standard rendering pattern (Success/Error/AccessDenied/NotFound), use the `toText()` extension
instead of repeating the `when` block. Commands with unique `AccessDenied` or `NotFound` text keep explicit `when`.

```kotlin
// Standard pattern — use toText()
val text = controller.doSomething(chatId, userId, args).toText()               // no prefix
val text = controller.register(chatId, userId, args).toText("✅ ")             // success prefix
val text = controller.getAll(chatId).toText(onError = { "❌ Custom: $it" })    // custom error

// Custom NotFound/AccessDenied — keep explicit when
val text = when (val r = controller.grantRole(chatId, userId, args)) {
    is CommandResponse.Success -> "✅ ${r.message}"
    is CommandResponse.AccessDenied -> "🚫 Лише адміни можуть призначати ролі."
    is CommandResponse.NotFound -> "❌ ${r.resource} '${r.identifier}' не знайдено."
    is CommandResponse.Error -> "❌ ${r.message}"
}

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
1. Create `bot/commands/{Name}Command.kt`
2. Create `controller/{Entity}Controller.kt` (if new domain area)
3. Register both with `single` in `di/PresentationModule.kt`
4. Add routing entry in `bot/handler/MessageHandler.kt`
