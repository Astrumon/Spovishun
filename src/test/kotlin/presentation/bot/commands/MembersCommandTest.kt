package presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.github.kotlintelegrambot.types.TelegramBotResult
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.commands.MembersCommand
import com.ua.astrumon.presentation.controller.MembersController
import com.ua.astrumon.presentation.util.BotAdminUtils
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MembersCommandTest {

    private val membersController: MembersController = mockk()
    private val botAdminUtils: BotAdminUtils = mockk()
    private val bot: Bot = mockk(relaxed = true)
    private lateinit var membersCommand: MembersCommand

    private val chatId = 123L
    private val userId = 456L

    @BeforeTest
    fun setup() {
        clearAllMocks()
        membersCommand = MembersCommand(membersController, botAdminUtils)
        every { botAdminUtils.getMemberRole(any(), any(), any()) } returns MemberRole.MEMBER
    }

    private fun createUpdate(
        fromUser: User? = User(id = userId, isBot = false, firstName = "Alice", username = "alice"),
        chatIdVal: Long = chatId,
        text: String = "/members"
    ): Update {
        val chat = Chat(id = chatIdVal, type = "group")
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = fromUser, text = text)
        return Update(updateId = 1L, message = message)
    }

    @Test
    fun `invoke should call controller and send message body`() = runTest {
        val update = createUpdate()
        coEvery { membersController.getMembers(chatId, any(), MemberRole.MEMBER) } returns CommandResponse.Success("members list")
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()

        membersCommand.execute(bot, update)

        coVerify { membersController.getMembers(chatId, any(), MemberRole.MEMBER) }
        coVerify { bot.sendMessage(ChatId.fromId(chatId), "members list", ParseMode.HTML) }
    }

    @Test
    fun `invoke should use user_id as username when username is null`() = runTest {
        val user = User(id = userId, isBot = false, firstName = "Alice", username = null)
        val update = createUpdate(fromUser = user)
        coEvery { membersController.getMembers(chatId, match { it.username == "user_$userId" }, MemberRole.MEMBER) } returns CommandResponse.Success("ok")
        every { bot.sendMessage(any(), any(), any()) } returns mockk<TelegramBotResult<Message>>()

        membersCommand.execute(bot, update)

        coVerify { membersController.getMembers(chatId, match { it.username == "user_$userId" }, MemberRole.MEMBER) }
    }

    @Test
    fun `invoke should return early when user is null`() = runTest {
        val update = createUpdate(fromUser = null)

        membersCommand.execute(bot, update)

        coVerify(exactly = 0) { membersController.getMembers(any(), any(), any()) }
        coVerify(exactly = 0) { bot.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `invoke should return early when message is null`() = runTest {
        val update = Update(updateId = 1L, message = null)

        membersCommand.execute(bot, update)

        coVerify(exactly = 0) { membersController.getMembers(any(), any(), any()) }
    }
}
