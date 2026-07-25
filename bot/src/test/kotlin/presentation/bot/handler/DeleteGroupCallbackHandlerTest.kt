package presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.handler.DeleteGroupCallbackHandler
import com.ua.astrumon.presentation.controller.GroupController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class DeleteGroupCallbackHandlerTest {
    private val bot: Bot = mockk(relaxed = true)
    private val groupController: GroupController = mockk()
    private lateinit var handler: DeleteGroupCallbackHandler

    private val chatId = 1L
    private val clickerId = 2L

    @BeforeTest
    fun setup() {
        clearAllMocks()
        handler = DeleteGroupCallbackHandler(groupController)
    }

    private fun update(data: String): Update = callbackUpdate(chatId, clickerId, data)

    @Test
    fun `should not delete when only group is selected`() = runTest {
        handler.handle(bot, update("delgroup:10"))

        coVerify(exactly = 0) { groupController.deleteGroupById(any(), any(), any()) }
    }

    @Test
    fun `should delete with parsed id on confirm`() = runTest {
        coEvery { groupController.deleteGroupById(chatId, clickerId, 10L) } returns CommandResponse.Success("ok")

        handler.handle(bot, update("delgroup:confirm:10"))

        coVerify(exactly = 1) { groupController.deleteGroupById(chatId, clickerId, 10L) }
    }

    @Test
    fun `should not delete on cancel`() = runTest {
        handler.handle(bot, update("delgroup:cancel"))

        coVerify(exactly = 0) { groupController.deleteGroupById(any(), any(), any()) }
    }

    @Test
    fun `should ignore non-numeric confirm id`() = runTest {
        handler.handle(bot, update("delgroup:confirm:abc"))

        coVerify(exactly = 0) { groupController.deleteGroupById(any(), any(), any()) }
    }

    @Test
    fun `should ack the callback query`() = runTest {
        handler.handle(bot, update("delgroup:cancel"))

        coVerify(exactly = 1) { bot.answerCallbackQuery("cb") }
    }
}
