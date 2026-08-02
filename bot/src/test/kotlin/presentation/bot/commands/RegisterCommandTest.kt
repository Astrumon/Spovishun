package presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.github.kotlintelegrambot.types.TelegramBotResult
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.commands.RegisterCommand
import com.ua.astrumon.presentation.controller.RegistrationController
import com.ua.astrumon.presentation.controller.RegistrationRequest
import com.ua.astrumon.presentation.util.BotAdminUtils
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import presentation.testMessagesProvider
import kotlin.test.BeforeTest
import kotlin.test.Test

class RegisterCommandTest {
    private val registrationController: RegistrationController = mockk()
    private val botAdminUtils: BotAdminUtils = mockk()
    private val bot: Bot = mockk(relaxed = true)
    private lateinit var registerCommand: RegisterCommand

    private val chatId = 123L
    private val userId = 456L
    private val request = RegistrationRequest(chatId, userId, "alice", "Alice", MemberRole.MEMBER)

    @BeforeTest
    fun setup() {
        clearAllMocks()
        registerCommand = RegisterCommand(registrationController, botAdminUtils, testMessagesProvider())
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()
        every { botAdminUtils.getMemberRole(any(), any(), any()) } returns MemberRole.MEMBER
    }

    private fun createUpdate(
        fromUser: User? = User(id = userId, isBot = false, firstName = "Alice", username = "alice"),
        chatIdVal: Long = chatId,
        text: String = "/register",
    ): Update {
        val chat = Chat(id = chatIdVal, type = "group")
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = fromUser, text = text)
        return Update(updateId = 1L, message = message)
    }

    @Test
    fun `invoke should delegate to controller and send success message with checkmark`() = runTest {
        val update = createUpdate()
        coEvery { registrationController.register(request, null) } returns
            CommandResponse.Success("Alice, зареєстровані в системі!")

        registerCommand.execute(bot, update)

        coVerify { registrationController.register(request, null) }
        coVerify {
            bot.sendMessage(ChatId.fromId(chatId), match { it.startsWith("✅") && it.contains("зареєстровані") }, ParseMode.HTML)
        }
    }

    @Test
    fun `invoke should send already registered message`() = runTest {
        val update = createUpdate()
        coEvery { registrationController.register(request, null) } returns
            CommandResponse.Success("Alice, ви вже зареєстровані в системі.")

        registerCommand.execute(bot, update)

        coVerify { bot.sendMessage(ChatId.fromId(chatId), match { it.contains("вже зареєстровані") }, ParseMode.HTML) }
    }

    @Test
    fun `invoke should use user_id as username when username is null`() = runTest {
        val user = User(id = userId, isBot = false, firstName = "Alice", username = null)
        val update = createUpdate(fromUser = user)
        val fallbackRequest = request.copy(username = "user_$userId")
        coEvery { registrationController.register(fallbackRequest, null) } returns CommandResponse.Success("ok")

        registerCommand.execute(bot, update)

        coVerify { registrationController.register(fallbackRequest, null) }
    }

    @Test
    fun `invoke should return early when user is null`() = runTest {
        val update = createUpdate(fromUser = null)

        registerCommand.execute(bot, update)

        coVerify(exactly = 0) { registrationController.register(any(), any()) }
        coVerify(exactly = 0) { bot.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `invoke should return early when message is null`() = runTest {
        val update = Update(updateId = 1L, message = null)

        registerCommand.execute(bot, update)

        coVerify(exactly = 0) { registrationController.register(any(), any()) }
    }

    @Test
    fun `invoke should pass birthday token to controller when b flag is present`() = runTest {
        val update = createUpdate(text = "/register \$b 01.01")
        coEvery { registrationController.register(request, "01.01") } returns CommandResponse.Success("ok")

        registerCommand.execute(bot, update)

        coVerify { registrationController.register(request, "01.01") }
    }

    @Test
    fun `invoke should accept the birthday flag in upper case`() = runTest {
        val update = createUpdate(text = "/register \$B 01.01")
        coEvery { registrationController.register(request, "01.01") } returns CommandResponse.Success("ok")

        registerCommand.execute(bot, update)

        coVerify { registrationController.register(request, "01.01") }
    }

    @Test
    fun `invoke should pass null token when b flag is absent`() = runTest {
        val update = createUpdate()
        coEvery { registrationController.register(request, null) } returns CommandResponse.Success("ok")

        registerCommand.execute(bot, update)

        coVerify { registrationController.register(request, null) }
    }

    @Test
    fun `invoke should reply with usage when b flag has no value`() = runTest {
        val update = createUpdate(text = "/register \$b")

        registerCommand.execute(bot, update)

        coVerify(exactly = 0) { registrationController.register(any(), any()) }
        coVerify { bot.sendMessage(ChatId.fromId(chatId), match { it.contains("Використання") }, ParseMode.HTML) }
    }

    @Test
    fun `invoke should reply with usage when b flag has extra arguments`() = runTest {
        val update = createUpdate(text = "/register \$b 01.01 junk")

        registerCommand.execute(bot, update)

        coVerify(exactly = 0) { registrationController.register(any(), any()) }
        coVerify { bot.sendMessage(ChatId.fromId(chatId), match { it.contains("Використання") }, ParseMode.HTML) }
    }

    @Test
    fun `invoke should ignore unrelated arguments and register as usual`() = runTest {
        val update = createUpdate(text = "/register привіт")
        coEvery { registrationController.register(request, null) } returns CommandResponse.Success("ok")

        registerCommand.execute(bot, update)

        coVerify { registrationController.register(request, null) }
    }
}
