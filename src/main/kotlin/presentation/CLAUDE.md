# presentation/

Contains: `bot/` (TelegramBot, MessageHandler, `commands/`), `controller/`, `util/` (BotAdminUtils).

## Forbidden imports
- Exposed / JDBC (`org.jetbrains.exposed.*`) — no DB access in this layer
- Any `domain/service/` import directly in a `Command` class

## Command flow
```
Command → Controller → returns String → Command sends to Telegram
```
1. **Command** — parse args from `Update`, call one `Controller` method, send the returned `String` to Telegram.
2. **Controller** — call `Service`(s), apply role checks, return a formatted `String`. Never returns Telegram types.
3. **BotAdminUtils** — query Telegram API only to derive initial role for a new member during registration.

```kotlin
// Correct command
class GrantRoleCommand(private val groupController: GroupController) {
    suspend operator fun invoke(bot: Bot, update: Update) {
        val args = update.message?.text?.split(" ")?.drop(1) ?: emptyList()
        val chatId = update.message?.chat?.id ?: return
        val response = groupController.grantRole(chatId, update.message!!.from!!.id, args)
        bot.sendMessage(chatId = ChatId.fromId(chatId), text = response, parseMode = ParseMode.HTML)
    }
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
