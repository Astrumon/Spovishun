package presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.handler.PingCallbackHandler
import com.ua.astrumon.presentation.bot.handler.ReadinessSessionRunner
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.controller.PingOutcome
import com.ua.astrumon.presentation.util.BotAdminUtils
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class PingCallbackHandlerTest {
    private val bot: Bot = mockk(relaxed = true)
    private val pingController: PingController = mockk()
    private val botAdminUtils: BotAdminUtils = mockk()
    private val readinessSessionRunner: ReadinessSessionRunner = mockk(relaxed = true)
    private lateinit var handler: PingCallbackHandler

    private val chatId = 1L
    private val clickerId = 2L

    @BeforeTest
    fun setup() {
        clearAllMocks()
        handler = PingCallbackHandler(pingController, botAdminUtils, readinessSessionRunner)
        every { botAdminUtils.getMemberRole(any(), any(), any()) } returns MemberRole.MEMBER
    }

    private fun update(data: String): Update = callbackUpdate(chatId, clickerId, data)

    @Test
    fun `should ping the whole chat when the all option is tapped`() = runTest {
        coEvery { pingController.pingAll(chatId, clickerId, any(), any(), MemberRole.MEMBER, emptyList()) } returns
            PingOutcome.Plain(CommandResponse.Success("📢 🗿\n\n@alice @bob"))

        handler.handle(bot, update("ping:${PingController.ALL_MEMBERS_ID}"))

        coVerify(exactly = 1) { pingController.pingAll(chatId, clickerId, any(), any(), MemberRole.MEMBER, emptyList()) }
        coVerify(exactly = 0) { pingController.pingGroupById(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should ping the tapped group`() = runTest {
        coEvery { pingController.pingGroupById(chatId, clickerId, any(), any(), MemberRole.MEMBER, 12L) } returns
            PingOutcome.Plain(CommandResponse.Success("📣 devs 🦞\n\n@alice"))

        handler.handle(bot, update("ping:12"))

        coVerify(exactly = 1) { pingController.pingGroupById(chatId, clickerId, any(), any(), MemberRole.MEMBER, 12L) }
        coVerify(exactly = 0) { pingController.pingAll(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should ack the callback query`() = runTest {
        handler.handle(bot, update("ping:abc"))

        coVerify(exactly = 1) { bot.answerCallbackQuery("cb") }
    }

    @Test
    fun `should ignore a non-numeric payload`() = runTest {
        handler.handle(bot, update("ping:abc"))

        coVerify(exactly = 0) { pingController.pingAll(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { pingController.pingGroupById(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should hand a readiness outcome to the runner and drop the picker message`() = runTest {
        val members = listOf(Member(1L, clickerId, "alice", "Alice"))
        coEvery { pingController.pingGroupById(chatId, clickerId, any(), any(), MemberRole.MEMBER, 12L) } returns
            PingOutcome.Readiness("📣 devs 🦞", members)

        handler.handle(bot, update("ping:12"))

        verify(exactly = 1) { bot.deleteMessage(ChatId.fromId(chatId), 5L) }
        verify(exactly = 1) { readinessSessionRunner.start(bot, chatId, "📣 devs 🦞", members) }
    }
}
