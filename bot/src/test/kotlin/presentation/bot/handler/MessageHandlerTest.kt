package presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.ua.astrumon.domain.bot.config.ChatAccessConfig
import com.ua.astrumon.presentation.bot.handler.MessageHandler
import com.ua.astrumon.presentation.util.ChatLogContext
import com.ua.astrumon.presentation.util.MemberAutoRegistrar
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.slf4j.MDC
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessageHandlerTest {
    private val autoRegistrar: MemberAutoRegistrar = mockk(relaxed = true)
    private val config: ChatAccessConfig = mockk(relaxed = true)
    private val bot: Bot = mockk(relaxed = true)
    private lateinit var messageHandler: MessageHandler

    private val chatId = 123L
    private val userId = 456L

    @BeforeTest
    fun setup() {
        clearAllMocks()
        MDC.clear()
        messageHandler = MessageHandler(autoRegistrar, config)
        every { config.allowedChatIds } returns emptySet()
    }

    @AfterTest
    fun tearDown() = MDC.clear()

    private fun createUpdate(
        fromUser: User? = User(id = userId, isBot = false, firstName = "Alice", username = "alice"),
        chatIdVal: Long = chatId,
    ): Update {
        val chat = Chat(id = chatIdVal, type = "group")
        val message = Message(messageId = 1L, date = 0L, chat = chat, from = fromUser, text = "hello")
        return Update(updateId = 1L, message = message)
    }

    @Test
    fun `handleIncomingMessage should auto-register the sender`() = runTest {
        val update = createUpdate()

        messageHandler.handleIncomingMessage(bot, update)

        coVerify(exactly = 1) {
            autoRegistrar.ensure(bot, match { it.id == chatId }, match { it.id == userId })
        }
    }

    @Test
    fun `handleIncomingMessage should expose the originating chat while registering`() = runTest {
        var chatIdDuringDispatch: String? = null
        var chatTypeDuringDispatch: String? = null
        coEvery { autoRegistrar.ensure(any(), any(), any()) } answers {
            chatIdDuringDispatch = MDC.get(ChatLogContext.CHAT_ID)
            chatTypeDuringDispatch = MDC.get(ChatLogContext.CHAT_TYPE)
        }

        messageHandler.handleIncomingMessage(bot, createUpdate())

        assertEquals(chatId.toString(), chatIdDuringDispatch)
        assertEquals("group", chatTypeDuringDispatch)
        assertNull(MDC.get(ChatLogContext.CHAT_ID))
        assertNull(MDC.get(ChatLogContext.CHAT_TYPE))
    }

    @Test
    fun `handleIncomingMessage should return early when message is null`() = runTest {
        messageHandler.handleIncomingMessage(bot, Update(updateId = 1L, message = null))

        coVerify(exactly = 0) { autoRegistrar.ensure(any(), any(), any()) }
    }

    @Test
    fun `handleIncomingMessage should return early when user is null`() = runTest {
        messageHandler.handleIncomingMessage(bot, createUpdate(fromUser = null))

        coVerify(exactly = 0) { autoRegistrar.ensure(any(), any(), any()) }
    }

    @Test
    fun `handleIncomingMessage should ignore chats outside the allow-list`() = runTest {
        every { config.allowedChatIds } returns setOf(chatId)

        messageHandler.handleIncomingMessage(bot, createUpdate(chatIdVal = 999L))

        coVerify(exactly = 0) { autoRegistrar.ensure(any(), any(), any()) }
    }
}
