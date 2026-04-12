package presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.presentation.util.BotAdminUtils
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.service.AutoRegisterService
import com.ua.astrumon.presentation.bot.handler.MessageHandler
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MessageHandlerTest {

    private val autoRegisterService: AutoRegisterService = mockk()
    private val botAdminUtils: BotAdminUtils = mockk()
    private val bot: Bot = mockk(relaxed = true)
    private lateinit var messageHandler: MessageHandler

    private val chatId = 123L
    private val userId = 456L

    @BeforeTest
    fun setup() {
        clearAllMocks()
        messageHandler = MessageHandler(autoRegisterService, botAdminUtils)
        every { botAdminUtils.getMemberRole(any(), any(), any()) } returns MemberRole.MEMBER
    }

    private fun createUpdate(
        fromUser: User? = User(id = userId, isBot = false, firstName = "Alice", username = "alice"),
        chatIdVal: Long = chatId
    ): Update {
        val chat = Chat(id = chatIdVal, type = "group")
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = fromUser, text = "hello")
        return Update(updateId = 1L, message = message)
    }

    @Test
    fun `handleIncomingMessage should auto-register user`() = runTest {
        // Given
        val update = createUpdate()
        val member = Member(1L, chatId, userId, "alice", "Alice", null)
        coEvery {
            autoRegisterService.ensureUserRegistered(
                chatId,
                userId,
                "alice",
                "Alice",
                MemberRole.MEMBER,
                null,
                "group"
            )
        } returns ResultContainer.success(member)

        // When
        messageHandler.handleIncomingMessage(bot, update)

        // Then
        coVerify {
            autoRegisterService.ensureUserRegistered(
                chatId,
                userId,
                "alice",
                "Alice",
                MemberRole.MEMBER,
                null,
                "group"
            )
        }
    }

    @Test
    fun `handleIncomingMessage should use user_id when username is null`() = runTest {
        // Given
        val user = User(id = userId, isBot = false, firstName = "Alice", username = null)
        val update = createUpdate(fromUser = user)
        val member = Member(1L, chatId, userId, "user_$userId", "Alice", null)
        coEvery {
            autoRegisterService.ensureUserRegistered(
                chatId,
                userId,
                "user_$userId",
                "Alice",
                MemberRole.MEMBER,
                null,
                "group"
            )
        } returns ResultContainer.success(member)

        // When
        messageHandler.handleIncomingMessage(bot, update)

        // Then
        coVerify {
            autoRegisterService.ensureUserRegistered(
                chatId,
                userId,
                "user_$userId",
                "Alice",
                MemberRole.MEMBER,
                null,
                "group"
            )
        }
    }

    @Test
    fun `handleIncomingMessage should return early when message is null`() = runTest {
        // Given
        val update = Update(updateId = 1L, message = null)

        // When
        messageHandler.handleIncomingMessage(bot, update)

        // Then
        coVerify(exactly = 0) { autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `handleIncomingMessage should return early when user is null`() = runTest {
        // Given
        val update = createUpdate(fromUser = null)

        // When
        messageHandler.handleIncomingMessage(bot, update)

        // Then
        coVerify(exactly = 0) { autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), any(), any()) }
    }
}
