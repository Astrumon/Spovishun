package presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.handler.CallbackContext
import com.ua.astrumon.presentation.bot.handler.RandomCallbackHandler
import com.ua.astrumon.presentation.controller.RandomController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import presentation.ukMessages
import kotlin.test.BeforeTest
import kotlin.test.Test

class RandomCallbackHandlerTest {
    private val bot: Bot = mockk(relaxed = true)
    private val randomController: RandomController = mockk()
    private lateinit var handler: RandomCallbackHandler

    private val chatId = 1L
    private val clickerId = 2L

    @BeforeTest
    fun setup() {
        clearAllMocks()
        handler = RandomCallbackHandler(randomController)
    }

    private fun ctx(payload: String): CallbackContext = callbackContext(chatId, clickerId, payload)

    @Test
    fun `should pick across the whole chat when the all-members option is tapped`() = runTest {
        coEvery { randomController.pickRandomAll(chatId) } returns CommandResponse.Success("🎲: @alice")

        handler.handle(bot, ctx("${RandomController.ALL_MEMBERS_ID}"), ukMessages)

        coVerify(exactly = 1) { randomController.pickRandomAll(chatId) }
        coVerify(exactly = 0) { randomController.pickRandomFromGroupById(any(), any()) }
    }

    @Test
    fun `should pick inside the tapped group`() = runTest {
        coEvery { randomController.pickRandomFromGroupById(chatId, 12L) } returns CommandResponse.Success("🎲: @bob")

        handler.handle(bot, ctx("12"), ukMessages)

        coVerify(exactly = 1) { randomController.pickRandomFromGroupById(chatId, 12L) }
        coVerify(exactly = 0) { randomController.pickRandomAll(any()) }
    }

    /** The router acks before dispatch now — a handler that also acked would answer the query twice. */
    @Test
    fun `should not answer the callback query itself`() = runTest {
        coEvery { randomController.pickRandomFromGroupById(chatId, 12L) } returns CommandResponse.Success("ok")

        handler.handle(bot, ctx("12"), ukMessages)

        verify(exactly = 0) { bot.answerCallbackQuery(any()) }
    }

    @Test
    fun `should ignore a non-numeric payload`() = runTest {
        handler.handle(bot, ctx("abc"), ukMessages)

        coVerify(exactly = 0) { randomController.pickRandomAll(any()) }
        coVerify(exactly = 0) { randomController.pickRandomFromGroupById(any(), any()) }
    }
}
