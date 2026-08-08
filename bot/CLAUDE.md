# :bot

Presentation module (Telegram). Depends on `:domain` and `:common`. Does NOT depend on `:data` —
repository wiring happens in `:app`.

Packages (under `presentation/`): `bot/` (TelegramBot, commands/, handler/), `controller/`,
`scheduler/`, `util/` (BotAdminUtils, MemberAutoRegistrar).

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

## Callback flow (spovishun-172)
```
CallbackRouter → ack + build CallbackContext + resolve BotMessages → CallbackHandler → Controller
```
`CallbackHandler.handle(bot, ctx, messages)` receives everything already parsed: the router picked
the handler by prefix, so re-parsing the `Update` inside each handler was duplication — and an
acknowledgement seven handlers could each silently forget.

`CallbackHandler.kind` says whether a tap **starts** an interaction or **continues** one, and that
single fact drives two behaviours:

| `kind` | Router acks up front | Router registers the tapper |
|---|---|---|
| `ENTRY_POINT` (default) | yes | yes — a picker press is an arrival like any command |
| `IN_PLACE` | no | no |

Exactly one handler is `IN_PLACE`: `ReadinessCallbackHandler`. A vote is a control inside an already
open poll — *when* the query is answered is the UI there (the pending query is the spinner, a
rejection is the answer's toast text), and its tapper was invited from the member table already or
is a bystander being turned away. The default covers every new handler without opting in.

`TwoStepMemberPicker` owns the `{ownerId}` → member list → `{ownerId}:{memberId}` → act flow. A
handler on it states only what differs — candidate source, step-2 prompt, action. Use it for a new
two-step member picker; `GrantRoleCallbackHandler` deliberately is not on it, because its step 2
selects a role rather than a member.

Group operations split by surface: `GroupController` for typed arguments, `GroupPickerController`
for picker listings and act-by-id. A command that opens a picker injects both.

## Auto-registration (spovishun-172)
Nobody calls `AutoRegisterService.ensureUserRegistered` from a controller. `MemberAutoRegistrar` is
the one implementation, applied at the three dispatch entry points — `MessageHandler`,
`AutoRegisterCommand` (wrapped onto every entry by `CommandRegistry`) and `CallbackRouter`. Adding a
command or a callback handler is the whole opt-in.

`ensureUserRegistered` takes the role as a **supplier**, not a value: deriving it costs a blocking
`getChatMember`, and the service only needs it when it actually creates a member. Passing
`botAdminUtils.getMemberRole(...)` eagerly would make every already-registered user pay for a
Telegram round trip on every command and every button tap.

`RegistrationController` is the one legitimate direct caller, and only for **other** users:
`StartCommand` pre-registers the chat's admins. The caller of `/start` is the decorator's job like
anyone else's.

One command opts out, via `registrationPolicy = RegistrationPolicy.COMMAND`: `/register`, whose
reply says whether the caller was *new*. Registering it first would make that reply always read
"already registered" — the distinction only exists before the dispatch decorator runs. Nothing else
should need this; a command that merely *uses* the member table wants the default.

**Testing consequence:** `command.execute(bot, update)` skips both decorators. Any test that depends
on the caller being registered — or on them *not* being — must go through `BaseIntegrationTest`'s
`dispatch(command, update)`, which wraps exactly as `CommandRegistry` does. Same for callbacks and
`dispatchCallback(handler, update)`.

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
| Commands | `ChatContextCommand`, applied to every entry by `CommandRegistry` — registering a command is enough. It wraps `AutoRegisterCommand`, so the registration logs carry the chat too |
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
4. Done — `TelegramBot` picks it up automatically via `CommandRegistry`, which also gives it the chat
   log context and caller auto-registration. Do not add either by hand.
