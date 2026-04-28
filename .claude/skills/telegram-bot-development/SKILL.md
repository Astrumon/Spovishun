---
name: telegram-bot-development
description: Use this skill when developing, debugging, or extending Telegram bots in the Spovishun project. Triggers on questions about Telegram Bot API, bot commands, message handling, inline keyboards, bot deployment, or update routing. Ukrainian triggers: "команда бота", "інлайн клавіатура", "обробник повідомлень", "реєстрація бота".
---

# Telegram Bot Development (Spovishun)

## Workflow

1. Identify the task type from the user request.
2. Pick the matching row in the Decision Table below.
3. Read the linked reference file in `references/`.
4. Apply the patterns from that reference together with the Always-Active Rules below.

## Decision Table

| If the task is about… | Read first |
|---|---|
| Bot registration, long-polling setup, initial wiring | `references/api-basics.md` |
| Parsing commands, routing updates, MessageHandler dispatch | `references/message-handlers.md` |
| Inline keyboards, callback queries, button flows | `references/keyboards.md` |
| Bot token security, input validation, deployment, shutdown | `references/security-deployment.md` |
| Suspend handlers, coroutine scope, dispatcher rules | `references/coroutines-integration.md` |

## Always-Active Rules

- Command flow: `TelegramBot → MessageHandler → Command → Controller → Service`
- Never call a `Service` directly from a `Command` — always route through a `Controller`
- `Controller` returns `CommandResponse`; `Command` owns emoji prefixes and final text assembly
- Never expose stack traces to users; send a generic error message instead
- `BOT_TOKEN` must come from an environment variable — never hardcode it

## Output Format

When implementing bot features:
1. **Architecture summary** — which layer is modified and why
2. **Code snippet** — the new command/handler/helper with Kotlin idioms
3. **Registration note** — if a new command needs to be registered in `MessageHandler`
4. **Test hint** — which `BaseIntegrationTest` pattern to follow

## Do NOT

- Do NOT load all references at once — pick exactly one based on the Decision Table.
- Do NOT call a `Service` from a `Command` — this violates the layer boundary.
- Do NOT hardcode `BOT_TOKEN` or any credentials in source files.
- Do NOT use `GlobalScope` for coroutines in handlers — use the injected scope.
- Do NOT skip `answerCallbackQuery` for callback queries, even if no popup is shown.

## Error Handling

- If no row in the Decision Table matches, ask the user to clarify before proceeding.
- If a reference file is missing, stop and report the exact path.
- Wrap all `execute()` calls in try-catch; handle `TelegramApiException` and `TelegramApiRequestException` separately.

## Related Skills

- `kotlin-specialist` — coroutine scope design, sealed classes for `CommandResponse`
- `postgresql-exposed-orm` — DB access patterns called from Services
- `unit-testing-kotlin` — integration test patterns using `BaseIntegrationTest`

## Example Invocation

- User: "Add a /stats command to the bot" → load `references/message-handlers.md`, implement Command → Controller → Service chain.
- User: "Build a confirm/cancel inline keyboard for a delete action" → load `references/keyboards.md`, apply callback data format and `answerCallbackQuery` pattern.
