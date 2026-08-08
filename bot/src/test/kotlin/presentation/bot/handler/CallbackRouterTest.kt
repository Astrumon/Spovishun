package presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.handler.CallbackContext
import com.ua.astrumon.presentation.bot.handler.CallbackHandler
import com.ua.astrumon.presentation.bot.handler.CallbackKind
import com.ua.astrumon.presentation.bot.handler.CallbackRouter
import com.ua.astrumon.presentation.util.ChatLogContext
import com.ua.astrumon.presentation.util.MemberAutoRegistrar
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.slf4j.MDC
import presentation.testMessagesProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CallbackRouterTest {
    private val bot: Bot = mockk(relaxed = true)
    private val pingHandler: CallbackHandler = mockk(relaxed = true)
    private val readinessHandler: CallbackHandler = mockk(relaxed = true)
    private val autoRegistrar: MemberAutoRegistrar = mockk(relaxed = true)

    private val callbackId = "cb-1"

    @AfterTest
    fun tearDown() = MDC.clear()

    @BeforeTest
    fun setup() {
        clearAllMocks()
        MDC.clear()
        every { pingHandler.prefix } returns "ping:"
        every { pingHandler.kind } returns CallbackKind.ENTRY_POINT
        every { readinessHandler.prefix } returns "ready:"
        every { readinessHandler.kind } returns CallbackKind.IN_PLACE
        coEvery { pingHandler.handle(any(), any(), any()) } returns Unit
        coEvery { readinessHandler.handle(any(), any(), any()) } returns Unit
    }

    private fun router(vararg handlers: CallbackHandler) = CallbackRouter(handlers.toList(), testMessagesProvider(), autoRegistrar)

    private fun buildUpdate(data: String): Update = callbackUpdate(chatId = 1L, clickerId = 2L, data = data, callbackId = callbackId)

    @Test
    fun `should dispatch to handler whose prefix matches`() = runTest {
        router(pingHandler, readinessHandler).route(bot, buildUpdate("ping:42"))

        coVerify(exactly = 1) { pingHandler.handle(eq(bot), any(), any()) }
        coVerify(exactly = 0) { readinessHandler.handle(any(), any(), any()) }
    }

    @Test
    fun `should dispatch only to the matching handler among several`() = runTest {
        router(pingHandler, readinessHandler).route(bot, buildUpdate("ready:go"))

        coVerify(exactly = 1) { readinessHandler.handle(eq(bot), any(), any()) }
        coVerify(exactly = 0) { pingHandler.handle(any(), any(), any()) }
    }

    @Test
    fun `should ack silently and dispatch nothing on unknown prefix`() = runTest {
        router(pingHandler, readinessHandler).route(bot, buildUpdate("unknown:x"))

        coVerify(exactly = 1) { bot.answerCallbackQuery(callbackId) }
        coVerify(exactly = 0) { pingHandler.handle(any(), any(), any()) }
        coVerify(exactly = 0) { readinessHandler.handle(any(), any(), any()) }
    }

    /** Acking here is what makes it impossible for a new handler to forget. */
    @Test
    fun `should ack before dispatching to an entry-point handler`() = runTest {
        router(pingHandler).route(bot, buildUpdate("ping:42"))

        coVerify(exactly = 1) { bot.answerCallbackQuery(callbackId) }
    }

    /** The readiness poll's spinner is the pending query — the router must leave it alone. */
    @Test
    fun `should leave the query unanswered for an in-place handler`() = runTest {
        router(readinessHandler).route(bot, buildUpdate("ready:a"))

        coVerify(exactly = 0) { bot.answerCallbackQuery(callbackId) }
        coVerify(exactly = 1) { readinessHandler.handle(eq(bot), any(), any()) }
    }

    @Test
    fun `should hand the handler a context with the prefix stripped and the clicker resolved`() = runTest {
        var received: CallbackContext? = null
        coEvery { pingHandler.handle(any(), any(), any()) } answers { received = secondArg() }

        router(pingHandler).route(bot, buildUpdate("ping:42"))

        val ctx = requireNotNull(received)
        assertEquals("42", ctx.payload)
        assertEquals(callbackId, ctx.queryId)
        assertEquals(1L, ctx.chatId)
        assertEquals(2L, ctx.clicker.id)
        assertEquals("user_2", ctx.clicker.username)
    }

    /** Anyone in the chat may tap a picker button, so that press is an arrival like any command. */
    @Test
    fun `should register the tapper of an entry-point handler`() = runTest {
        router(pingHandler).route(bot, buildUpdate("ping:42"))

        coVerify(exactly = 1) { autoRegistrar.ensure(eq(bot), any(), any()) }
    }

    /**
     * A readiness vote is a control inside an open poll — every voter was invited from the member
     * table already, and a bystander is turned away rather than welcomed in. Registering here would
     * put a lookup on the hot path of every tap for nothing.
     */
    @Test
    fun `should not register the tapper of an in-place handler`() = runTest {
        router(readinessHandler).route(bot, buildUpdate("ready:a"))

        coVerify(exactly = 0) { autoRegistrar.ensure(any(), any(), any()) }
    }

    @Test
    fun `should expose the originating chat while the handler runs`() = runTest {
        var chatIdDuringDispatch: String? = null
        var chatTypeDuringDispatch: String? = null
        coEvery { pingHandler.handle(any(), any(), any()) } answers {
            chatIdDuringDispatch = MDC.get(ChatLogContext.CHAT_ID)
            chatTypeDuringDispatch = MDC.get(ChatLogContext.CHAT_TYPE)
        }

        router(pingHandler).route(bot, buildUpdate("ping:42"))

        assertEquals("1", chatIdDuringDispatch)
        assertEquals("group", chatTypeDuringDispatch)
        assertNull(MDC.get(ChatLogContext.CHAT_ID))
        assertNull(MDC.get(ChatLogContext.CHAT_TYPE))
    }

    @Test
    fun `should be a no-op when callbackQuery is null`() = runTest {
        router(pingHandler).route(bot, Update(updateId = 1L, callbackQuery = null))

        coVerify(exactly = 0) { bot.answerCallbackQuery(any()) }
        coVerify(exactly = 0) { pingHandler.handle(any(), any(), any()) }
    }
}
