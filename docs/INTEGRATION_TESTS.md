# Integration Tests

## What they cover

Integration tests verify the full pipeline for every bot command:

```
Command → Controller → Service → MockImpl Repository
              ↓
    bot.sendMessage (captured via MockK)
```

Unlike unit tests that mock services, integration tests use **real services and real in-memory repositories**. This catches bugs that span multiple layers — e.g., permission checks, auto-registration chains, or group membership lookups.

The only mocked boundary is the Telegram API (`Bot` and `BotAdminUtils`), since those make HTTP calls to Telegram servers. No real bot token or test chat is required.

## Running

```bash
# Run integration tests only
./gradlew integrationTest

# Run both unit and integration tests
./gradlew test integrationTest
```

Reports are generated at `build/reports/tests/integrationTest/`.

## Tests per command

| Command | Test file | Tests |
|---------|-----------|-------|
| `/start` | `StartCommandIntegrationTest` | 5 |
| `/register` | `RegisterCommandIntegrationTest` | 3 |
| `/all`, `/ping` | `PingCommandIntegrationTest` | 5 |
| `/groups`, `/newgroup`, `/delgroup`, `/addtogroup`, `/removefromgroup` | `GroupCommandIntegrationTest` | 9 |
| `/grantrole` | `GrantRoleCommandIntegrationTest` | 4 |
| `/members` | `MembersCommandIntegrationTest` | 3 |
| Text messages | `MessageHandlerIntegrationTest` | 3 |

## Adding a new integration test

1. Create a test class in `src/integrationTest/kotlin/commands/`
2. Extend `BaseIntegrationTest`
3. Use `buildUpdate(text, userId, username, ...)` to craft Telegram updates
4. Use `registerMember(...)` to pre-seed the repository
5. Invoke the command directly: `myCommand(bot, update)` or `myCommand.methodName(bot, update)`
6. Assert on `bot.sendMessage(...)` via MockK `verify { }` or check repository state

```kotlin
class MyCommandIntegrationTest : BaseIntegrationTest() {

    @Test
    fun `my command should do something`() = runTest {
        registerMember(role = MemberRole.ADMIN)
        val update = buildUpdate("/mycommand arg1")

        myCommand(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("expected text") },
                ParseMode.HTML
            )
        }
    }
}
```

## Test isolation

Each test gets a fresh set of in-memory repositories via `@BeforeTest` in `BaseIntegrationTest`. There is no shared state between tests. No database, no `.env` file, and no network calls are needed.

## CI

Integration tests run automatically on every PR to `develop` or `main` via GitHub Actions (see `.github/workflows/ci.yml`). No secrets are required.
