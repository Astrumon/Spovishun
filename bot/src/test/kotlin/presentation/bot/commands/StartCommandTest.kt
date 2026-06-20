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
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.commands.StartCommand
import com.ua.astrumon.presentation.controller.RegistrationController
import com.ua.astrumon.presentation.util.BotAdminUtils
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class StartCommandTest {
    private val registrationController: RegistrationController = mockk()
    private val botAdminUtils: BotAdminUtils = mockk()
    private val bot: Bot = mockk(relaxed = true)
    private lateinit var startCommand: StartCommand

    private val chatId = 123L
    private val userId = 456L
    private val user = User(id = userId, isBot = false, firstName = "Alice", username = "alice")

    @BeforeTest
    fun setup() {
        clearAllMocks()
        startCommand = StartCommand(registrationController, botAdminUtils)
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()
        every { botAdminUtils.getMemberRole(any(), any(), any()) } returns MemberRole.MEMBER
        every { bot.getChat(any()) } returns TelegramBotResult.Success(Chat(id = chatId, type = "private"))
        coEvery { registrationController.start(any(), any(), any(), any(), any()) } returns CommandResponse.Success("Spovishun активний!")
        coEvery { registrationController.ensureUserRegistered(any(), any(), any(), any(), any()) } returns Unit
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

        coVerify { registrationController.start(chatId, userId, "alice", "Alice", MemberRole.MEMBER) }
        coVerify { bot.sendMessage(ChatId.fromId(chatId), "Spovishun активний!", ParseMode.HTML) }
    }

    @Test
    fun `invoke should use user_id as username when username is null`() = runTest {
        val noUsernameUser = User(id = userId, isBot = false, firstName = "Alice", username = null)
        val update = createUpdate(fromUser = noUsernameUser)

        startCommand.execute(bot, update)

        coVerify { registrationController.start(chatId, userId, "user_$userId", "Alice", MemberRole.MEMBER) }
    }

    @Test
    fun `invoke should return early when user is null`() = runTest {
        val update = createUpdate(fromUser = null)

        startCommand.execute(bot, update)

        coVerify(exactly = 0) { registrationController.start(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { bot.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `invoke should return early when message is null`() = runTest {
        val update = Update(updateId = 1L, message = null)

        startCommand.execute(bot, update)

        coVerify(exactly = 0) { registrationController.start(any(), any(), any(), any(), any()) }
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

        coVerify { registrationController.ensureUserRegistered(chatId, 789L, "admin", "Admin", MemberRole.ADMIN) }
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

        coVerify { registrationController.ensureUserRegistered(chatId, 789L, "admin", "Admin", MemberRole.ADMIN) }
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

    @Test
    fun `invoke should sanitize username with special characters`() = runTest {
        val specialUser = User(id = userId, isBot = false, firstName = "Alice", username = "al!ce@#")
        val update = createUpdate(fromUser = specialUser)

        startCommand.execute(bot, update)

        coVerify { registrationController.start(chatId, userId, "al_ce__", "Alice", MemberRole.MEMBER) }
    }
}
