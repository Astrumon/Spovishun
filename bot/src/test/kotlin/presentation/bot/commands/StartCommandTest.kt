package presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ChatMember
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.github.kotlintelegrambot.types.TelegramBotResult
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.commands.StartCommand
import com.ua.astrumon.presentation.controller.RegistrationController
import com.ua.astrumon.presentation.controller.RegistrationRequest
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import presentation.testMessagesProvider
import kotlin.test.BeforeTest
import kotlin.test.Test

class StartCommandTest {
    private val registrationController: RegistrationController = mockk()
    private val bot: Bot = mockk(relaxed = true)
    private lateinit var startCommand: StartCommand

    private val chatId = 123L
    private val userId = 456L
    private val user = User(id = userId, isBot = false, firstName = "Alice", username = "alice")

    @BeforeTest
    fun setup() {
        clearAllMocks()
        startCommand = StartCommand(registrationController, testMessagesProvider())
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()
        every { bot.getChat(any()) } returns TelegramBotResult.Success(Chat(id = chatId, type = "private"))
        coEvery { registrationController.start(any()) } returns CommandResponse.Success("Spovishun активний!")
        coEvery { registrationController.ensureUserRegistered(any()) } returns Unit
    }

    private fun createUpdate(
        fromUser: User? = user,
        chatIdVal: Long = chatId,
        chatType: String = "private",
    ): Update {
        val chat = Chat(id = chatIdVal, type = chatType)
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = fromUser, text = "/start")
        return Update(updateId = 1L, message = message)
    }

    @Test
    fun `invoke should delegate to controller and send welcome message`() = runTest {
        val update = createUpdate()

        startCommand.execute(bot, update)

        coVerify { registrationController.start(chatId) }
        coVerify { bot.sendMessage(ChatId.fromId(chatId), "Spovishun активний!", ParseMode.HTML) }
    }

    @Test
    fun `invoke should return early when user is null`() = runTest {
        val update = createUpdate(fromUser = null)

        startCommand.execute(bot, update)

        coVerify(exactly = 0) { registrationController.start(any()) }
        coVerify(exactly = 0) { bot.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `invoke should return early when message is null`() = runTest {
        val update = Update(updateId = 1L, message = null)

        startCommand.execute(bot, update)

        coVerify(exactly = 0) { registrationController.start(any()) }
    }

    @Test
    fun `invoke should sync admins for group chat`() = runTest {
        val adminUser = User(id = 789L, isBot = false, firstName = "Admin", username = "admin")
        every { bot.getChat(ChatId.fromId(chatId)) } returns TelegramBotResult.Success(Chat(id = chatId, type = "group"))
        every { bot.getChatAdministrators(ChatId.fromId(chatId)) } returns TelegramBotResult.Success(
            listOf(ChatMember(user = adminUser, status = "administrator")),
        )
        val update = createUpdate(chatType = "group")

        startCommand.execute(bot, update)

        coVerify {
            registrationController.ensureUserRegistered(
                RegistrationRequest(chatId, 789L, "admin", "Admin", MemberRole.ADMIN),
            )
        }
    }

    @Test
    fun `invoke should sync admins for supergroup chat`() = runTest {
        val adminUser = User(id = 789L, isBot = false, firstName = "Admin", username = "admin")
        every { bot.getChat(ChatId.fromId(chatId)) } returns TelegramBotResult.Success(Chat(id = chatId, type = "supergroup"))
        every { bot.getChatAdministrators(ChatId.fromId(chatId)) } returns TelegramBotResult.Success(
            listOf(ChatMember(user = adminUser, status = "administrator")),
        )
        val update = createUpdate(chatType = "supergroup")

        startCommand.execute(bot, update)

        coVerify {
            registrationController.ensureUserRegistered(
                RegistrationRequest(chatId, 789L, "admin", "Admin", MemberRole.ADMIN),
            )
        }
    }

    @Test
    fun `invoke should send registration invitation for group chat`() = runTest {
        every { bot.getChat(ChatId.fromId(chatId)) } returns TelegramBotResult.Success(Chat(id = chatId, type = "group"))
        every { bot.getChatAdministrators(ChatId.fromId(chatId)) } returns TelegramBotResult.Success(emptyList())
        val update = createUpdate(chatType = "group")

        startCommand.execute(bot, update)

        coVerify { bot.sendMessage(ChatId.fromId(chatId), match { it.contains("Реєстрація учасників") }, ParseMode.HTML) }
    }

    @Test
    fun `invoke should not send invitation for supergroup chat`() = runTest {
        every { bot.getChat(ChatId.fromId(chatId)) } returns TelegramBotResult.Success(Chat(id = chatId, type = "supergroup"))
        every { bot.getChatAdministrators(ChatId.fromId(chatId)) } returns TelegramBotResult.Success(emptyList())
        val update = createUpdate(chatType = "supergroup")

        startCommand.execute(bot, update)

        coVerify(exactly = 0) { bot.sendMessage(any(), match { it.contains("Реєстрація учасників") }, any()) }
    }

    /**
     * Admin pre-registration is best-effort: the catch around it is narrow precisely so a Telegram
     * failure there cannot swallow the caller's own registration and welcome (spovishun-172).
     */
    @Test
    fun `invoke should still welcome the caller when reading the admins throws`() = runTest {
        every { bot.getChat(ChatId.fromId(chatId)) } returns TelegramBotResult.Success(Chat(id = chatId, type = "supergroup"))
        every { bot.getChatAdministrators(ChatId.fromId(chatId)) } throws IllegalStateException("telegram unreachable")

        startCommand.execute(bot, createUpdate(chatType = "supergroup"))

        coVerify(exactly = 0) { registrationController.ensureUserRegistered(any()) }
        coVerify { registrationController.start(chatId) }
        coVerify { bot.sendMessage(ChatId.fromId(chatId), "Spovishun активний!", ParseMode.HTML) }
    }

    @Test
    fun `invoke should still welcome the caller when reading the chat throws`() = runTest {
        every { bot.getChat(ChatId.fromId(chatId)) } throws IllegalStateException("telegram unreachable")

        startCommand.execute(bot, createUpdate())

        coVerify { registrationController.start(chatId) }
        coVerify { bot.sendMessage(ChatId.fromId(chatId), "Spovishun активний!", ParseMode.HTML) }
    }
}
