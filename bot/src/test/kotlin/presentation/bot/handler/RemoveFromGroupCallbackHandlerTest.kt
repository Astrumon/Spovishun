package presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.handler.CallbackContext
import com.ua.astrumon.presentation.bot.handler.RemoveFromGroupCallbackHandler
import com.ua.astrumon.presentation.controller.GroupPickerController
import com.ua.astrumon.presentation.controller.PickerListing
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import presentation.ukMessages
import kotlin.test.BeforeTest
import kotlin.test.Test

class RemoveFromGroupCallbackHandlerTest {
    private val bot: Bot = mockk(relaxed = true)
    private val groupController: GroupPickerController = mockk()
    private lateinit var handler: RemoveFromGroupCallbackHandler

    private val chatId = 1L
    private val clickerId = 2L

    @BeforeTest
    fun setup() {
        clearAllMocks()
        handler = RemoveFromGroupCallbackHandler(groupController)
    }

    private fun ctx(payload: String): CallbackContext = callbackContext(chatId, clickerId, payload)

    @Test
    fun `should open group-scoped member picker when only group is selected`() = runTest {
        coEvery { groupController.groupMembersForPicker(chatId, clickerId, 10L) } returns PickerListing.Show(emptyList())

        handler.handle(bot, ctx("10"), ukMessages)

        coVerify(exactly = 1) { groupController.groupMembersForPicker(chatId, clickerId, 10L) }
        coVerify(exactly = 0) { groupController.removeUserFromGroupById(any(), any(), any(), any()) }
    }

    @Test
    fun `should remove with parsed group and member ids`() = runTest {
        coEvery { groupController.removeUserFromGroupById(chatId, clickerId, 10L, 20L) } returns CommandResponse.Success("ok")

        handler.handle(bot, ctx("10:20"), ukMessages)

        coVerify(exactly = 1) { groupController.removeUserFromGroupById(chatId, clickerId, 10L, 20L) }
    }

    @Test
    fun `should ignore non-numeric member id`() = runTest {
        handler.handle(bot, ctx("10:abc"), ukMessages)

        coVerify(exactly = 0) { groupController.removeUserFromGroupById(any(), any(), any(), any()) }
        coVerify(exactly = 0) { groupController.groupMembersForPicker(any(), any(), any()) }
    }
}
