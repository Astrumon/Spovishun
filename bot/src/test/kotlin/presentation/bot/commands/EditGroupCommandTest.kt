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
import com.ua.astrumon.presentation.bot.commands.EditGroupCommand
import com.ua.astrumon.presentation.controller.GroupSettingsController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import presentation.testMessagesProvider
import kotlin.test.BeforeTest
import kotlin.test.Test

class EditGroupCommandTest {
    private val groupSettingsController: GroupSettingsController = mockk()
    private val bot: Bot = mockk(relaxed = true)
    private lateinit var command: EditGroupCommand

    private val chatId = 123L
    private val userId = 456L
    private val user = User(id = userId, isBot = false, firstName = "Alice", username = "alice")

    @BeforeTest
    fun setup() {
        clearAllMocks()
        command = EditGroupCommand(groupSettingsController, testMessagesProvider())
    }

    private fun createUpdate(
        fromUser: User? = user,
        text: String = "/editg",
    ): Update {
        val chat = Chat(id = chatId, type = "group")
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = fromUser, text = text)
        return Update(updateId = 1L, message = message)
    }

    @Test
    fun `should pass args to controller and prefix success with checkmark`() = runTest {
        val args = listOf("devs", "\$icon", "🔥")
        val update = createUpdate(text = "/editg devs \$icon 🔥")
        coEvery { groupSettingsController.editGroup(chatId, userId, args) } returns CommandResponse.Success("Іконка групи <b>devs</b>: 🔥")
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()

        command.execute(bot, update)

        coVerify { groupSettingsController.editGroup(chatId, userId, args) }
        coVerify { bot.sendMessage(ChatId.fromId(chatId), match { it.startsWith("✅") }, ParseMode.HTML) }
    }

    @Test
    fun `should send access denied message when access denied`() = runTest {
        val args = listOf("devs", "\$icon", "🔥")
        val update = createUpdate(text = "/editg devs \$icon 🔥")
        coEvery { groupSettingsController.editGroup(chatId, userId, args) } returns CommandResponse.AccessDenied("moderator")
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()

        command.execute(bot, update)

        coVerify { bot.sendMessage(ChatId.fromId(chatId), match { it.contains("🚫") }, ParseMode.HTML) }
    }

    @Test
    fun `should render a not-found group as a not-found message`() = runTest {
        val args = listOf("nope", "\$icon", "🔥")
        val update = createUpdate(text = "/editg nope \$icon 🔥")
        coEvery { groupSettingsController.editGroup(chatId, userId, args) } returns CommandResponse.NotFound("Група", "nope")
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()

        command.execute(bot, update)

        coVerify { bot.sendMessage(ChatId.fromId(chatId), match { it.contains("nope") }, ParseMode.HTML) }
    }

    @Test
    fun `should pass empty args when no arguments`() = runTest {
        val update = createUpdate(text = "/editg")
        coEvery { groupSettingsController.editGroup(chatId, userId, emptyList()) } returns CommandResponse.Error("usage")
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()

        command.execute(bot, update)

        coVerify { groupSettingsController.editGroup(chatId, userId, emptyList()) }
    }

    @Test
    fun `should return early when user is null`() = runTest {
        val update = createUpdate(fromUser = null)

        command.execute(bot, update)

        coVerify(exactly = 0) { groupSettingsController.editGroup(any(), any(), any()) }
    }
}
