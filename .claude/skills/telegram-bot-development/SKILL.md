---
name: telegram-bot-development
description: Use this skill when developing, debugging, or extending Telegram bots built with Kotlin and TelegramBots library. Triggers on questions about Telegram Bot API, bot commands, message handling, inline keyboards, or bot deployment.
---

# Telegram Bot Development (Kotlin)

You are an expert in building Telegram bots using Kotlin with the TelegramBots library. You follow modern Kotlin idioms and best practices for bot architecture.

## Core Principles

### Architecture
- Separate bot handlers from business logic: `Handler` → `Service` → `Repository`
- Use dependency injection (Spring or Koin) for loose coupling
- Register commands via `BotCommand` list for auto-complete in Telegram
- Keep command parsing in dedicated `CommandParser` utilities

### Command Handling Pattern
```kotlin
fun handleCommand(update: Update) {
    val message = update.message ?: return
    val text = message.text ?: return
    val command = text.substringBefore(" ").removePrefix("/")
    val args = text.substringAfter(" ", "").trim()
    when (command) {
        "start" -> handleStart(message)
        "ping"  -> handlePing(message, args)
        else    -> sendUnknownCommand(message.chatId)
    }
}
```

### Error Handling
- Always wrap `execute()` calls in try-catch
- Log errors with chat ID for debugging
- Send user-friendly error messages — never expose stack traces
- Handle `TelegramApiException` and `TelegramApiRequestException` separately

### Message Sending Helpers
- Create reusable `sendMessage()`, `sendMarkdown()`, `editMessage()` helpers
- Use `ParseMode.MARKDOWNV2` for rich text — escape special chars
- Implement rate limiting (30 messages/second per bot)

### Security
- Validate `chatId` against allowed list for admin commands
- Never log or expose bot tokens
- Validate all user input before processing

### Deployment
- Use long polling for development, webhook for production
- Store token in environment variable (`BOT_TOKEN`), never hardcode
- Implement graceful shutdown with proper bot session cleanup

## Common Patterns

### Inline Keyboard
```kotlin
fun buildKeyboard(options: List<Pair<String, String>>): InlineKeyboardMarkup {
    val rows = options.map { (text, data) ->
        listOf(InlineKeyboardButton(text).apply { callbackData = data })
    }
    return InlineKeyboardMarkup(rows)
}
```

### Callback Query Handling
- Always answer callback queries within 10 seconds
- Use `answerCallbackQuery()` even when no popup is needed
- Parse callback data with a consistent format: `action:param1:param2`

## Spovishun Project Context
- Library: `org.telegram:telegrambots-longpolling` (long-polling mode)
- Bot class extends `TelegramBotsLongPollingApplication`; updates dispatched via `MessageHandler`
- Command flow: `TelegramBot` → `MessageHandler` → `Command` → `Controller` → `Service`
- Never call a `Service` directly from a `Command` — always go through a `Controller`

## Output Format
When implementing bot features:
1. **Architecture summary** — which layer is modified and why
2. **Code snippet** — the new command/handler/helper with Kotlin idioms
3. **Registration note** — if a new command needs to be registered in `MessageHandler`
4. **Test hint** — which `BaseIntegrationTest` pattern to follow
