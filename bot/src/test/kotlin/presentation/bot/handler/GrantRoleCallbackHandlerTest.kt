package presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.handler.CallbackContext
import com.ua.astrumon.presentation.bot.handler.GrantRoleCallbackHandler
import com.ua.astrumon.presentation.controller.GroupController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import presentation.ukMessages
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

    private fun ctx(payload: String): CallbackContext = callbackContext(chatId, clickerId, payload)

    @Test
    fun `should show role picker without granting when only member is selected`() = runTest {
        handler.handle(bot, ctx("20"), ukMessages)

        coVerify(exactly = 0) { groupController.grantRoleById(any(), any(), any(), any()) }
    }

    @Test
    fun `should grant parsed role to parsed member`() = runTest {
        coEvery { groupController.grantRoleById(chatId, clickerId, 20L, MemberRole.MODERATOR) } returns CommandResponse.Success("ok")

        handler.handle(bot, ctx("20:MODERATOR"), ukMessages)

        coVerify(exactly = 1) { groupController.grantRoleById(chatId, clickerId, 20L, MemberRole.MODERATOR) }
    }

    @Test
    fun `should ignore unknown role name`() = runTest {
        handler.handle(bot, ctx("20:BOGUS"), ukMessages)

        coVerify(exactly = 0) { groupController.grantRoleById(any(), any(), any(), any()) }
    }
}
