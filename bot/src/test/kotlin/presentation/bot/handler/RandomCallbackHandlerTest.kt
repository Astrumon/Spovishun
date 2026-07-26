package presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.handler.RandomCallbackHandler
import com.ua.astrumon.presentation.controller.RandomController
import com.ua.astrumon.presentation.util.BotAdminUtils
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class RandomCallbackHandlerTest {
    private val bot: Bot = mockk(relaxed = true)
    private val randomController: RandomController = mockk()
    private val botAdminUtils: BotAdminUtils = mockk()
    private lateinit var handler: RandomCallbackHandler

    private val chatId = 1L
    private val clickerId = 2L

    @BeforeTest
    fun setup() {
        clearAllMocks()
        handler = RandomCallbackHandler(randomController, botAdminUtils)
        every { botAdminUtils.getMemberRole(any(), any(), any()) } returns MemberRole.MEMBER
    }

    private fun update(data: String): Update = callbackUpdate(chatId, clickerId, data)

    @Test
    fun `should pick across the whole chat when the all-members option is tapped`() = runTest {
        coEvery { randomController.pickRandomAll(chatId, clickerId, any(), any(), MemberRole.MEMBER) } returns
            CommandResponse.Success("🎲: @alice")

        handler.handle(bot, update("random:${RandomController.ALL_MEMBERS_ID}"))

        coVerify(exactly = 1) { randomController.pickRandomAll(chatId, clickerId, any(), any(), MemberRole.MEMBER) }
        coVerify(exactly = 0) { randomController.pickRandomFromGroupById(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should pick inside the tapped group`() = runTest {
        coEvery { randomController.pickRandomFromGroupById(chatId, clickerId, any(), any(), MemberRole.MEMBER, 12L) } returns
            CommandResponse.Success("🎲: @bob")

        handler.handle(bot, update("random:12"))

        coVerify(exactly = 1) { randomController.pickRandomFromGroupById(chatId, clickerId, any(), any(), MemberRole.MEMBER, 12L) }
        coVerify(exactly = 0) { randomController.pickRandomAll(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should ack the callback query`() = runTest {
        handler.handle(bot, update("random:abc"))

        coVerify(exactly = 1) { bot.answerCallbackQuery("cb") }
    }

    @Test
    fun `should ignore a non-numeric payload`() = runTest {
        handler.handle(bot, update("random:abc"))

        coVerify(exactly = 0) { randomController.pickRandomAll(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { randomController.pickRandomFromGroupById(any(), any(), any(), any(), any(), any()) }
    }
}
