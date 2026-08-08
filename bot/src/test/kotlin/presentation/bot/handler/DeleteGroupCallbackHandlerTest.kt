package presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.handler.CallbackContext
import com.ua.astrumon.presentation.bot.handler.DeleteGroupCallbackHandler
import com.ua.astrumon.presentation.controller.GroupPickerController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import presentation.ukMessages
import kotlin.test.BeforeTest
import kotlin.test.Test

class DeleteGroupCallbackHandlerTest {
    private val bot: Bot = mockk(relaxed = true)
    private val groupController: GroupPickerController = mockk()
    private lateinit var handler: DeleteGroupCallbackHandler

    private val chatId = 1L
    private val clickerId = 2L

    @BeforeTest
    fun setup() {
        clearAllMocks()
        handler = DeleteGroupCallbackHandler(groupController)
    }

    private fun ctx(payload: String): CallbackContext = callbackContext(chatId, clickerId, payload)

    @Test
    fun `should not delete when only group is selected`() = runTest {
        handler.handle(bot, ctx("10"), ukMessages)

        coVerify(exactly = 0) { groupController.deleteGroupById(any(), any(), any()) }
    }

    @Test
    fun `should delete with parsed id on confirm`() = runTest {
        coEvery { groupController.deleteGroupById(chatId, clickerId, 10L) } returns CommandResponse.Success("ok")

        handler.handle(bot, ctx("confirm:10"), ukMessages)

        coVerify(exactly = 1) { groupController.deleteGroupById(chatId, clickerId, 10L) }
    }

    @Test
    fun `should not delete on cancel`() = runTest {
        handler.handle(bot, ctx("cancel"), ukMessages)

        coVerify(exactly = 0) { groupController.deleteGroupById(any(), any(), any()) }
    }

    @Test
    fun `should ignore non-numeric confirm id`() = runTest {
        handler.handle(bot, ctx("confirm:abc"), ukMessages)

        coVerify(exactly = 0) { groupController.deleteGroupById(any(), any(), any()) }
    }

    /** The router acks before dispatch now — a handler that also acked would answer the query twice. */
    @Test
    fun `should not answer the callback query itself`() = runTest {
        handler.handle(bot, ctx("cancel"), ukMessages)

        verify(exactly = 0) { bot.answerCallbackQuery(any()) }
    }
}
