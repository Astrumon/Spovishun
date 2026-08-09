package commands

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import infrastructure.BaseIntegrationTest
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RandomCommandIntegrationTest : BaseIntegrationTest() {
    @Test
    fun `random with only invoker registered should mention the invoker`() = runTest {
        val update = buildUpdate("/random")

        dispatch(randomCommand, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("@$testUsername") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `random with registered members should mention one of them`() = runTest {
        registerMember(userId = 1L, username = "alice")
        registerMember(userId = 2L, username = "bob")
        val update = buildUpdate("/random")

        randomCommand.execute(bot, update)

        // /random auto-registers the invoker, so the candidate pool is {invoker, alice, bob};
        // assert the pick is one of those registered members (not just alice/bob).
        val candidates = listOf(testUsername, "alice", "bob")
        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { message -> candidates.any { message.contains("@$it") } },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `random with nonexistent group should send group not found message`() = runTest {
        val update = buildUpdate("/random ghostgroup")

        randomCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("не знайдено") },
                ParseMode.HTML,
            )
        }
    }
}
