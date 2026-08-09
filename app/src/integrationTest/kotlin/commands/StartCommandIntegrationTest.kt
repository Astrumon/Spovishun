package commands

import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ChatMember
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.github.kotlintelegrambot.types.TelegramBotResult
import infrastructure.BaseIntegrationTest
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class StartCommandIntegrationTest : BaseIntegrationTest() {
    @Test
    fun `start should send welcome message containing bot name`() = runTest {
        val update = buildUpdate("/start")

        startCommand.execute(bot, update)

        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("Spovishun на місці") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `start should auto-register the calling user via full service stack`() = runTest {
        val update = buildUpdate("/start")

        dispatch(startCommand, update)

        val member = memberService.getMemberByUsername(testUsername).getOrThrow()
        assertTrue(member.userId == testUserId)
    }

    @Test
    fun `start in group chat should register admins returned from telegram`() = runTest {
        val update = buildUpdate("/start", chatType = "group")
        val adminUser = User(
            id = testAdminId,
            isBot = false,
            firstName = "Admin",
            username = testAdminUsername,
        )
        val adminMember = ChatMember(user = adminUser, status = "administrator")
        every { bot.getChat(ChatId.fromId(testChatId)) } returns
            TelegramBotResult.Success(
                Chat(id = testChatId, type = "group"),
            )
        every { bot.getChatAdministrators(ChatId.fromId(testChatId)) } returns
            TelegramBotResult
                .Success(listOf(adminMember))

        startCommand.execute(bot, update)

        val adminInRepo = memberService.getMemberByUsername(testAdminUsername)
        assertTrue(adminInRepo.isSuccess)
    }

    @Test
    fun `start in group chat should send registration invitation`() = runTest {
        val update = buildUpdate("/start", chatType = "group")
        every { bot.getChat(ChatId.fromId(testChatId)) } returns
            TelegramBotResult.Success(
                Chat(id = testChatId, type = "group"),
            )
        every { bot.getChatAdministrators(ChatId.fromId(testChatId)) } returns
            TelegramBotResult
                .Success(emptyList())

        startCommand.execute(bot, update)

        verify(atLeast = 1) {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                match { it.contains("Реєстрація учасників") },
                ParseMode.HTML,
            )
        }
    }

    @Test
    fun `start with null message should do nothing`() = runTest {
        val update = Update(updateId = 1L, message = null)

        startCommand.execute(bot, update)

        verify(exactly = 0) { bot.sendMessage(any(), any<String>(), any()) }
    }
}
