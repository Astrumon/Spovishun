package presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.handler.GrantRoleCallbackHandler
import com.ua.astrumon.presentation.controller.GroupController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class GrantRoleCallbackHandlerTest {
    private val bot: Bot = mockk(relaxed = true)
    private val groupController: GroupController = mockk()
    private lateinit var handler: GrantRoleCallbackHandler

    private val chatId = 1L
    private val clickerId = 2L

    @BeforeTest
    fun setup() {
        clearAllMocks()
        handler = GrantRoleCallbackHandler(groupController)
    }

    private fun update(data: String): Update = callbackUpdate(chatId, clickerId, data)

    @Test
    fun `should show role picker without granting when only member is selected`() = runTest {
        handler.handle(bot, update("grant:20"))

        coVerify(exactly = 0) { groupController.grantRoleById(any(), any(), any(), any()) }
    }

    @Test
    fun `should grant parsed role to parsed member`() = runTest {
        coEvery { groupController.grantRoleById(chatId, clickerId, 20L, MemberRole.MODERATOR) } returns CommandResponse.Success("ok")

        handler.handle(bot, update("grant:20:MODERATOR"))

        coVerify(exactly = 1) { groupController.grantRoleById(chatId, clickerId, 20L, MemberRole.MODERATOR) }
    }

    @Test
    fun `should ignore unknown role name`() = runTest {
        handler.handle(bot, update("grant:20:BOGUS"))

        coVerify(exactly = 0) { groupController.grantRoleById(any(), any(), any(), any()) }
    }
}
