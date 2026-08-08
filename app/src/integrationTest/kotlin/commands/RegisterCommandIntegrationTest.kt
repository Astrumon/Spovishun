package commands

import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.ua.astrumon.domain.bot.model.BirthDate
import infrastructure.BaseIntegrationTest
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegisterCommandIntegrationTest : BaseIntegrationTest() {
    @Test
    fun `register should save new member and send success response`() = runTest {
        val update = buildUpdate("/register")

        dispatch(registerCommand, update)

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

        dispatch(registerCommand, update)

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
        val user = User(
            id = testUserId,
            isBot = false,
            firstName = testFirstName,
            username = null,
        )
        val chat = Chat(id = testChatId, type = "supergroup")
        val message = Message(
            messageId = 1L,
            date = 0L,
            chat = chat,
            from = user,
            text = "/register",
        )
        val nullUsernameUpdate = Update(updateId = 1L, message = message)

        registerCommand.execute(bot, nullUsernameUpdate)

        val fallbackUsername = "user_$testUserId"
        val member = memberService.getMemberByUsername(fallbackUsername).getOrThrow()
        assertTrue(member.userId == testUserId)
    }

    @Test
    fun `register with birthday flag should save member and birthday in one command`() = runTest {
        val update = buildUpdate("/register \$b 01.01")

        registerCommand.execute(bot, update)

        val member = memberService.getMemberByUsername(testUsername).getOrThrow()
        assertEquals(BirthDate(1, 1), member.birthday)
        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("зареєстровані") && it.contains("День народження збережено") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `register with invalid birthday flag should not register the member`() = runTest {
        val update = buildUpdate("/register \$b abc")

        registerCommand.execute(bot, update)

        assertTrue(memberService.getMemberByUsername(testUsername).isFailure)
        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("формат") },
                ParseMode.HTML,
            )
        }
    }
}
