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
import com.ua.astrumon.presentation.bot.commands.DeleteGroupCommand
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.controller.GroupPickerController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import presentation.testMessagesProvider
import kotlin.test.BeforeTest
import kotlin.test.Test

class DeleteGroupCommandTest {
    private val groupController: GroupController = mockk()
    private val groupPickerController: GroupPickerController = mockk()
    private val bot: Bot = mockk(relaxed = true)
    private lateinit var command: DeleteGroupCommand

    private val chatId = 123L
    private val userId = 456L
    private val user = User(id = userId, isBot = false, firstName = "Alice", username = "alice")

    @BeforeTest
    fun setup() {
        clearAllMocks()
        command = DeleteGroupCommand(groupController, groupPickerController, testMessagesProvider())
    }

    private fun createUpdate(
        fromUser: User? = user,
        text: String = "/delgroup",
    ): Update {
        val chat = Chat(id = chatId, type = "group")
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = fromUser, text = text)
        return Update(updateId = 1L, message = message)
    }

    @Test
    fun `should pass args to controller and prefix success`() = runTest {
        val update = createUpdate(text = "/delgroup devs")
        coEvery { groupController.deleteGroup(chatId, userId, listOf("devs")) } returns
            CommandResponse.Success("Група <b>devs</b> видалена.")
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()

        command.execute(bot, update)

        coVerify { groupController.deleteGroup(chatId, userId, listOf("devs")) }
        coVerify { bot.sendMessage(ChatId.fromId(chatId), match { it.startsWith("🗑") }, ParseMode.HTML) }
    }

    @Test
    fun `should return early when user is null`() = runTest {
        val update = createUpdate(fromUser = null)

        command.execute(bot, update)

        coVerify(exactly = 0) { groupController.deleteGroup(any(), any(), any()) }
    }
}
