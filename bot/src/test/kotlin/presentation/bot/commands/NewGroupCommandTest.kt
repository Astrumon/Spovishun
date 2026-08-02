package presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.github.kotlintelegrambot.types.TelegramBotResult
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.commands.NewGroupCommand
import com.ua.astrumon.presentation.controller.GroupController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import presentation.testMessagesProvider
import kotlin.test.BeforeTest
import kotlin.test.Test

class NewGroupCommandTest {
    private val groupController: GroupController = mockk()
    private val bot: Bot = mockk(relaxed = true)
    private lateinit var command: NewGroupCommand

    private val chatId = 123L
    private val userId = 456L
    private val user = User(id = userId, isBot = false, firstName = "Alice", username = "alice")

    @BeforeTest
    fun setup() {
        clearAllMocks()
        command = NewGroupCommand(groupController, testMessagesProvider())
    }

    private fun createUpdate(
        fromUser: User? = user,
        text: String = "/newgroup",
    ): Update {
        val chat = Chat(id = chatId, type = "group")
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = fromUser, text = text)
        return Update(updateId = 1L, message = message)
    }

    @Test
    fun `should pass args to controller and prefix success with checkmark`() = runTest {
        val update = createUpdate(text = "/newgroup devs")
        coEvery { groupController.createGroup(chatId, userId, listOf("devs")) } returns
            CommandResponse.Success("Група <b>devs</b> створена!")
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()

        command.execute(bot, update)

        coVerify { groupController.createGroup(chatId, userId, listOf("devs")) }
        coVerify { bot.sendMessage(ChatId.fromId(chatId), match { it.startsWith("✅") }, ParseMode.HTML) }
    }

    @Test
    fun `should send access denied message when access denied`() = runTest {
        val update = createUpdate(text = "/newgroup devs")
        coEvery { groupController.createGroup(chatId, userId, listOf("devs")) } returns CommandResponse.AccessDenied("moderator")
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()

        command.execute(bot, update)

        coVerify { bot.sendMessage(ChatId.fromId(chatId), match { it.contains("🚫") }, ParseMode.HTML) }
    }

    @Test
    fun `should pass empty args when no arguments`() = runTest {
        val update = createUpdate(text = "/newgroup")
        coEvery { groupController.createGroup(chatId, userId, emptyList()) } returns CommandResponse.Error("usage")
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()

        command.execute(bot, update)

        coVerify { groupController.createGroup(chatId, userId, emptyList()) }
    }

    @Test
    fun `should return early when user is null`() = runTest {
        val update = createUpdate(fromUser = null)

        command.execute(bot, update)

        coVerify(exactly = 0) { groupController.createGroup(any(), any(), any()) }
    }
}
