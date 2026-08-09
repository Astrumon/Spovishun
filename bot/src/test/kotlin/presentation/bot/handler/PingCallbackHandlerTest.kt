package presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.handler.CallbackContext
import com.ua.astrumon.presentation.bot.handler.PingCallbackHandler
import com.ua.astrumon.presentation.bot.handler.ReadinessSession
import com.ua.astrumon.presentation.bot.handler.ReadinessSessionRunner
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.controller.PingOutcome
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import presentation.ukMessages
import kotlin.test.BeforeTest
import kotlin.test.Test

class PingCallbackHandlerTest {
    private val bot: Bot = mockk(relaxed = true)
    private val pingController: PingController = mockk()
    private val readinessSessionRunner: ReadinessSessionRunner = mockk(relaxed = true)
    private lateinit var handler: PingCallbackHandler

    private val chatId = 1L
    private val clickerId = 2L

    @BeforeTest
    fun setup() {
        clearAllMocks()
        handler = PingCallbackHandler(pingController, readinessSessionRunner)
    }

    private fun ctx(payload: String): CallbackContext = callbackContext(chatId, clickerId, payload)

    @Test
    fun `should ping the whole chat when the all option is tapped`() = runTest {
        coEvery { pingController.pingAll(chatId, emptyList()) } returns
            PingOutcome.Plain(CommandResponse.Success("📢 🗿\n\n@alice @bob"))

        handler.handle(bot, ctx("${PingController.ALL_MEMBERS_ID}"), ukMessages)

        coVerify(exactly = 1) { pingController.pingAll(chatId, emptyList()) }
        coVerify(exactly = 0) { pingController.pingGroupById(any(), any()) }
    }

    @Test
    fun `should ping the tapped group`() = runTest {
        coEvery { pingController.pingGroupById(chatId, 12L) } returns
            PingOutcome.Plain(CommandResponse.Success("📣 devs 🦞\n\n@alice"))

        handler.handle(bot, ctx("12"), ukMessages)

        coVerify(exactly = 1) { pingController.pingGroupById(chatId, 12L) }
        coVerify(exactly = 0) { pingController.pingAll(any(), any()) }
    }

    /** The router acks before dispatch now — a handler that also acked would answer the query twice. */
    @Test
    fun `should not answer the callback query itself`() = runTest {
        coEvery { pingController.pingGroupById(chatId, 12L) } returns
            PingOutcome.Plain(CommandResponse.Success("ok"))

        handler.handle(bot, ctx("12"), ukMessages)

        verify(exactly = 0) { bot.answerCallbackQuery(any()) }
    }

    @Test
    fun `should ignore a non-numeric payload`() = runTest {
        handler.handle(bot, ctx("abc"), ukMessages)

        coVerify(exactly = 0) { pingController.pingAll(any(), any()) }
        coVerify(exactly = 0) { pingController.pingGroupById(any(), any()) }
    }

    @Test
    fun `should hand a readiness outcome to the runner and drop the picker message`() = runTest {
        val members = listOf(Member(1L, clickerId, "alice", "Alice"))
        coEvery { pingController.pingGroupById(chatId, 12L) } returns PingOutcome.Readiness("📣 devs 🦞", members)

        handler.handle(bot, ctx("12"), ukMessages)

        verify(exactly = 1) { bot.deleteMessage(ChatId.fromId(chatId), 5L) }
        verify(exactly = 1) {
            readinessSessionRunner.start(bot, chatId, ReadinessSession(ukMessages, "📣 devs 🦞", members), clickerId)
        }
    }
}
