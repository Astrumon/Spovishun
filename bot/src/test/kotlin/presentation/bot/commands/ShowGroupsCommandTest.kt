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
import com.ua.astrumon.presentation.bot.commands.ShowGroupsCommand
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

class ShowGroupsCommandTest {
    private val groupController: GroupController = mockk()
    private val bot: Bot = mockk(relaxed = true)
    private lateinit var command: ShowGroupsCommand

    private val chatId = 123L
    private val userId = 456L
    private val user = User(id = userId, isBot = false, firstName = "Alice", username = "alice")

    @BeforeTest
    fun setup() {
        clearAllMocks()
        command = ShowGroupsCommand(groupController, testMessagesProvider())
    }

    private fun createUpdate(fromUser: User? = user): Update {
        val chat = Chat(id = chatId, type = "group")
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = fromUser, text = "/groups")
        return Update(updateId = 1L, message = message)
    }

    @Test
    fun `should call controller and send message body`() = runTest {
        val update = createUpdate()
        coEvery { groupController.getGroups(chatId) } returns CommandResponse.Success("groups list")
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()

        command.execute(bot, update)

        coVerify { groupController.getGroups(chatId) }
        coVerify { bot.sendMessage(ChatId.fromId(chatId), "groups list", ParseMode.HTML) }
    }

    @Test
    fun `should return early when message is null`() = runTest {
        command.execute(bot, Update(updateId = 1L, message = null))

        coVerify(exactly = 0) { groupController.getGroups(any()) }
    }
}
