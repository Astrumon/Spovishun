package commands

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import infrastructure.BaseIntegrationTest
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class RegisterCommandIntegrationTest : BaseIntegrationTest() {
    @Test
    fun `register should save new member and send success response`() = runTest {
        val update = buildUpdate("/register")

        registerCommand.execute(bot, update)

        val member = memberService.getMemberByUsername(testUsername).getOrThrow()
        assertTrue(member.userId == testUserId)
        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("успішно зареєстровані") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `register when already registered should send already-registered response`() = runTest {
        registerMember()
        val update = buildUpdate("/register")

        registerCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("вже зареєстровані") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `register with null username should store user_id fallback`() = runTest {
        val update = buildUpdate("/register", username = null.toString())
        // Build update with actual null username
        val user = com.github.kotlintelegrambot.entities.User(
            id = testUserId,
            isBot = false,
            firstName = testFirstName,
            username = null,
        )
        val chat = com.github.kotlintelegrambot.entities
            .Chat(id = testChatId, type = "supergroup")
        val message = com.github.kotlintelegrambot.entities.Message(
            messageId = 1L,
            date = 0L,
            chat = chat,
            from = user,
            text = "/register",
        )
        val nullUsernameUpdate = com.github.kotlintelegrambot.entities
            .Update(updateId = 1L, message = message)

        registerCommand.execute(bot, nullUsernameUpdate)

        val fallbackUsername = "user_$testUserId"
        val member = memberService.getMemberByUsername(fallbackUsername).getOrThrow()
        assertTrue(member.userId == testUserId)
    }
}
