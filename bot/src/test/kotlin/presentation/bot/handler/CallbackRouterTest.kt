package presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.CallbackQuery
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.ua.astrumon.presentation.bot.handler.CallbackHandler
import com.ua.astrumon.presentation.bot.handler.CallbackRouter
import com.ua.astrumon.presentation.util.ChatLogContext
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

class CallbackRouterTest {
    private val bot: Bot = mockk(relaxed = true)
    private val pingHandler: CallbackHandler = mockk(relaxed = true)
    private val readinessHandler: CallbackHandler = mockk(relaxed = true)

    private val callbackId = "cb-1"

    @AfterTest
    fun tearDown() = MDC.clear()

    @BeforeTest
    fun setup() {
        clearAllMocks()
        MDC.clear()
        every { pingHandler.prefix } returns "ping:"
        every { readinessHandler.prefix } returns "ready:"
        coEvery { pingHandler.handle(any(), any()) } returns Unit
        coEvery { readinessHandler.handle(any(), any()) } returns Unit
    }

    private fun buildUpdate(data: String): Update {
        val chat = Chat(id = 1L, type = "group")
        val message = Message(messageId = 1L, date = 0L, chat = chat)
        val callbackQuery = CallbackQuery(
            id = callbackId,
            from = User(id = 2L, isBot = false, firstName = "Alice"),
            message = message,
            inlineMessageId = null,
            data = data,
            chatInstance = "instance",
        )
        return Update(updateId = 1L, callbackQuery = callbackQuery)
    }

    @Test
    fun `should dispatch to handler whose prefix matches`() = runTest {
        val router = CallbackRouter(listOf(pingHandler, readinessHandler))
        val update = buildUpdate("ping:42")

        router.route(bot, update)

        coVerify(exactly = 1) { pingHandler.handle(bot, update) }
        coVerify(exactly = 0) { readinessHandler.handle(any(), any()) }
    }

    @Test
    fun `should dispatch only to the matching handler among several`() = runTest {
        val router = CallbackRouter(listOf(pingHandler, readinessHandler))
        val update = buildUpdate("ready:go")

        router.route(bot, update)

        coVerify(exactly = 1) { readinessHandler.handle(bot, update) }
        coVerify(exactly = 0) { pingHandler.handle(any(), any()) }
    }

    @Test
    fun `should ack silently and dispatch nothing on unknown prefix`() = runTest {
        val router = CallbackRouter(listOf(pingHandler, readinessHandler))
        val update = buildUpdate("unknown:x")

        router.route(bot, update)

        coVerify(exactly = 1) { bot.answerCallbackQuery(callbackId) }
        coVerify(exactly = 0) { pingHandler.handle(any(), any()) }
        coVerify(exactly = 0) { readinessHandler.handle(any(), any()) }
    }

    @Test
    fun `should expose the originating chat while the handler runs`() = runTest {
        var chatIdDuringDispatch: String? = null
        var chatTypeDuringDispatch: String? = null
        coEvery { pingHandler.handle(any(), any()) } answers {
            chatIdDuringDispatch = MDC.get(ChatLogContext.CHAT_ID)
            chatTypeDuringDispatch = MDC.get(ChatLogContext.CHAT_TYPE)
        }
        val router = CallbackRouter(listOf(pingHandler))

        router.route(bot, buildUpdate("ping:42"))

        assertEquals("1", chatIdDuringDispatch)
        assertEquals("group", chatTypeDuringDispatch)
        assertNull(MDC.get(ChatLogContext.CHAT_ID))
        assertNull(MDC.get(ChatLogContext.CHAT_TYPE))
    }

    @Test
    fun `should be a no-op when callbackQuery is null`() = runTest {
        val router = CallbackRouter(listOf(pingHandler))
        val update = Update(updateId = 1L, callbackQuery = null)

        router.route(bot, update)

        coVerify(exactly = 0) { bot.answerCallbackQuery(any()) }
        coVerify(exactly = 0) { pingHandler.handle(any(), any()) }
    }
}
