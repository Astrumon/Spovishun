package presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.handler.AddToGroupCallbackHandler
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.controller.PickerListing
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class AddToGroupCallbackHandlerTest {
    private val bot: Bot = mockk(relaxed = true)
    private val groupController: GroupController = mockk()
    private lateinit var handler: AddToGroupCallbackHandler

    private val chatId = 1L
    private val clickerId = 2L

    @BeforeTest
    fun setup() {
        clearAllMocks()
        handler = AddToGroupCallbackHandler(groupController)
    }

    private fun update(data: String): Update = callbackUpdate(chatId, clickerId, data)

    @Test
    fun `should open member picker when only group is selected`() = runTest {
        coEvery { groupController.chatMembersForModeratorPicker(chatId, clickerId) } returns PickerListing.Show(emptyList())

        handler.handle(bot, update("addto:10"))

        coVerify(exactly = 1) { groupController.chatMembersForModeratorPicker(chatId, clickerId) }
        coVerify(exactly = 0) { groupController.addUserToGroupById(any(), any(), any(), any()) }
    }

    @Test
    fun `should add with parsed group and member ids`() = runTest {
        coEvery { groupController.addUserToGroupById(chatId, clickerId, 10L, 20L) } returns CommandResponse.Success("ok")

        handler.handle(bot, update("addto:10:20"))

        coVerify(exactly = 1) { groupController.addUserToGroupById(chatId, clickerId, 10L, 20L) }
    }

    @Test
    fun `should ignore non-numeric member id`() = runTest {
        handler.handle(bot, update("addto:10:abc"))

        coVerify(exactly = 0) { groupController.addUserToGroupById(any(), any(), any(), any()) }
    }
}
