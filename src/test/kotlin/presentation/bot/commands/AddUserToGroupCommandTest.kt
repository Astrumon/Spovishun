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
import com.ua.astrumon.presentation.bot.commands.AddUserToGroupCommand
import com.ua.astrumon.presentation.controller.GroupController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class AddUserToGroupCommandTest {

    private val groupController: GroupController = mockk()
    private val bot: Bot = mockk(relaxed = true)
    private lateinit var command: AddUserToGroupCommand

    private val chatId = 123L
    private val userId = 456L
    private val user = User(id = userId, isBot = false, firstName = "Alice", username = "alice")

    @BeforeTest
    fun setup() {
        clearAllMocks()
        command = AddUserToGroupCommand(groupController)
    }

    private fun createUpdate(fromUser: User? = user, text: String = "/addtogroup"): Update {
        val chat = Chat(id = chatId, type = "group")
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = fromUser, text = text)
        return Update(updateId = 1L, message = message)
    }

    @Test
    fun `should pass args to controller`() = runTest {
        val update = createUpdate(text = "/addtogroup devs @bob")
        coEvery { groupController.addUserToGroup(chatId, userId, listOf("devs", "@bob")) } returns CommandResponse.Success("bob додано до devs")
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()

        command.execute(bot, update)

        coVerify { groupController.addUserToGroup(chatId, userId, listOf("devs", "@bob")) }
    }

    @Test
    fun `should return early when user is null`() = runTest {
        val update = createUpdate(fromUser = null)

        command.execute(bot, update)

        coVerify(exactly = 0) { groupController.addUserToGroup(any(), any(), any()) }
    }
}
