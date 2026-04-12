# E2E Tests

Real end-to-end tests that interact with the Telegram Bot API.

## Architecture

```
Test (JUnit)                            Main Bot (in-process, real kotlin-telegram-bot)
  │                                         │
  │── dispatch("/start", syntheticUpdate) ──▶│
  │                                         │── real bot.getChatAdministrators(chatId)
  │                                         │── real bot.getChatMember(chatId, userId)
  │                                         │── real bot.sendMessage(response)
  │                                         │── writes state to in-memory MockImpl repos
  │◀── allMembers() / allGroups() ──────────│
  │
  assert(state changed as expected)
```

- **Main bot** runs in-process with a real `Bot` instance (actual Telegram API calls: `sendMessage`, `getChatMember`, `getChatAdministrators`).
- **Commands are dispatched directly** via synthetic `Update` objects — bypasses the Telegram bot-to-bot message limitation (bots cannot read each other's messages via `getUpdates`).
- **Verification is repository-state based** — assertions check in-memory `MockImpl` repo contents, not `getUpdates` polling.
- **Helper bot** is used only to obtain its Telegram user ID (via `getMe`) for synthetic update construction.
- The real `BotAdminUtils` calls `bot.getChatMember()` against the live test group, so both bots must be group admins.

---

## Prerequisites

### 1. Create two Telegram bots via BotFather

1. Open [@BotFather](https://t.me/BotFather) in Telegram.
2. `/newbot` → name your main test bot (e.g. `SpovishunTestBot`) → save the token as `TEST_BOT_TOKEN`.
3. `/newbot` → name your helper bot (e.g. `SpovishunHelperBot`) → save the token as `TEST_HELPER_BOT_TOKEN`.

### 2. Disable privacy mode for both bots

For each bot, in BotFather:
```
/mybots → select the bot → Bot Settings → Group Privacy → Turn off
```
This allows the helper bot to see messages sent by the main bot in the group.

### 3. Create a test Telegram group

1. Create a new Telegram group (e.g. `Spovishun E2E Test`).
2. Add both bots to the group.
3. Promote both bots to **administrators** (so `getChatMember` admin checks work correctly).

### 4. Get the test chat ID

Send any message to the group, then call:
```
https://api.telegram.org/bot<TEST_HELPER_BOT_TOKEN>/getUpdates
```
Look for `"chat":{"id": -100XXXXXXXXX}` — that negative number is your `TEST_CHAT_ID`.

### 5. Get admin user IDs (optional)

`TEST_ADMINS` is a comma-separated list of Telegram user IDs that should be treated as bot admins. You can leave it empty for tests.

---

## Environment Variables

| Variable | Description | Example |
|---|---|---|
| `TEST_BOT_TOKEN` | Main test bot token | `8409395637:AAGaY_HH...` |
| `TEST_HELPER_BOT_TOKEN` | Helper bot token | `8028686149:AAEmnWAM...` |
| `TEST_CHAT_ID` | Negative ID of the test group | `-1001234567890` |
| `TEST_ADMINS` | Comma-separated admin user IDs (optional) | `123456789,987654321` |

---

## Running Locally

```bash
export TEST_BOT_TOKEN="your_main_test_bot_token"
export TEST_HELPER_BOT_TOKEN="your_helper_bot_token"
export TEST_CHAT_ID="-1001234567890"
export TEST_ADMINS="123456789"

./gradlew e2eTest
```

Or inline:
```bash
TEST_BOT_TOKEN=xxx TEST_HELPER_BOT_TOKEN=yyy TEST_CHAT_ID=-100zzz ./gradlew e2eTest
```

Tests are **skipped** (not failed) when environment variables are not set, so `./gradlew e2eTest` is safe to run in any environment.

---

## Running in CI (GitHub Actions)

The workflow `.github/workflows/e2e.yml` runs:
- **Manually** via `workflow_dispatch` from the Actions tab.
- **On a weekly schedule** (Mondays at 06:00 UTC).

Add these repository secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `TEST_BOT_TOKEN` | Main test bot token |
| `TEST_HELPER_BOT_TOKEN` | Helper bot token |
| `TEST_CHAT_ID` | Test group chat ID |
| `TEST_ADMINS` | Admin user IDs (optional) |

The `concurrency` group ensures only one e2e run executes at a time, preventing races on the shared test group.

---

## Test Structure

```
src/e2eTest/kotlin/
  infrastructure/
    E2EConfig.kt             — reads env vars
    TelegramHelperBot.kt     — Ktor HTTP helper: sendCommand, waitForBotResponse
    BaseE2ETest.kt           — lifecycle: start/stop main bot, expose sendAndExpect()
  commands/
    StartCommandE2ETest.kt
    RegisterCommandE2ETest.kt
    PingCommandE2ETest.kt
    GroupCommandE2ETest.kt
    GrantRoleCommandE2ETest.kt
    MembersCommandE2ETest.kt
```

---

## Troubleshooting

**Tests fail with `getChatMember` / `getChatAdministrators` errors**
- Both bots must be **administrators** in the test group.
- Verify `TEST_CHAT_ID` is the correct group ID (must be negative, e.g. `-1001234567890`).
- The main bot and helper bot tokens must be different.

**`🚫 Лише адміни та модератори.` — command rejected**
- Group commands check the member's role in the in-memory repository.
- Tests that require admin access (`GroupCommandE2ETest`, `GrantRoleCommandE2ETest`) pre-register the helper bot with `ADMIN` role in `@BeforeTest`.

**`bot.sendMessage` throws or returns an error**
- The main bot token must be valid and the bot must be a member of the test group.
- Check that `TEST_BOT_TOKEN` and `TEST_CHAT_ID` are correctly set.

**Note on bot-to-bot message delivery**
- Telegram does **not** deliver messages sent by one bot to another bot via `getUpdates`, even with privacy mode disabled.
- This is why verification is done via repository state (not `getUpdates` polling).
