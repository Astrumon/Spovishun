package presentation.util

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.User
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.model.MemberWithChat
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.presentation.util.BotAdminUtils
import com.ua.astrumon.presentation.util.MemberAutoRegistrar
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MemberAutoRegistrarTest {
    private val autoRegisterService: AutoRegisterService = mockk()
    private val botAdminUtils: BotAdminUtils = mockk()
    private val bot: Bot = mockk(relaxed = true)
    private lateinit var registrar: MemberAutoRegistrar

    private val chatId = 123L
    private val userId = 456L
    private val chat = Chat(id = chatId, type = "group", title = "Team")
    private val member = MemberWithChat(1L, userId, "alice", "Alice", MemberRole.MEMBER, null)

    @BeforeTest
    fun setup() {
        clearAllMocks()
        registrar = MemberAutoRegistrar(autoRegisterService, botAdminUtils)
        every { botAdminUtils.getMemberRole(any(), any(), any()) } returns MemberRole.ADMIN
        coEvery {
            autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), any(), any(), any())
        } returns ResultContainer.success(member)
    }

    private fun user(username: String? = "alice") = User(id = userId, isBot = false, firstName = "Alice", username = username)

    @Test
    fun `should register with the chat title and type`() = runTest {
        registrar.ensure(bot, chat, user())

        coVerify(exactly = 1) {
            autoRegisterService.ensureUserRegistered(chatId, userId, "alice", "Alice", any(), "Team", "group")
        }
    }

    @Test
    fun `should fall back to a user_id handle when the username is missing`() = runTest {
        registrar.ensure(bot, chat, user(username = null))

        coVerify(exactly = 1) {
            autoRegisterService.ensureUserRegistered(chatId, userId, "user_$userId", "Alice", any(), any(), any())
        }
    }

    /**
     * Deriving the role costs a blocking `getChatMember`. Passing it as a supplier is what keeps an
     * already-registered user — the overwhelmingly common case — from paying for one (spovishun-172).
     */
    @Test
    fun `should not resolve the role until the service asks for it`() = runTest {
        val resolveRole = slot<suspend () -> MemberRole>()
        coEvery {
            autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), capture(resolveRole), any(), any())
        } returns ResultContainer.success(member)

        registrar.ensure(bot, chat, user())

        verify(exactly = 0) { botAdminUtils.getMemberRole(any(), any(), any()) }
        assertEquals(MemberRole.ADMIN, resolveRole.captured.invoke())
        verify(exactly = 1) { botAdminUtils.getMemberRole(bot, chatId, userId) }
    }
}
