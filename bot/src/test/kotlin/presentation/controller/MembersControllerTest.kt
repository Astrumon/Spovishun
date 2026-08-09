package presentation.controller

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.model.MemberWithChat
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.controller.MembersController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import presentation.testMessagesProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class MembersControllerTest {
    private val memberService: MemberService = mockk()
    private lateinit var membersController: MembersController

    private val chatId = 123L

    @BeforeTest
    fun setup() {
        clearAllMocks()
        membersController = MembersController(memberService, testMessagesProvider())
    }

    @Test
    fun `getMembers should return Success with formatted list and role badges`() = runTest {
        val members = listOf(
            MemberWithChat(1L, 456L, "alice", "Alice", MemberRole.ADMIN, null),
            MemberWithChat(2L, 789L, "bob", "Bob", MemberRole.MODERATOR, null),
            MemberWithChat(3L, 111L, "charlie", "Charlie", MemberRole.MEMBER, null),
        )
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(members)

        val result = membersController.getMembers(chatId)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Зареєстровані учасники:"))
        assertTrue(result.message.contains("@alice 🔐"))
        assertTrue(result.message.contains("@bob 🛡"))
        assertTrue(result.message.contains("@charlie"))
        assertTrue(result.message.contains("Всього: 3 учасників"))
    }

    @Test
    fun `getMembers should show firstName for user_ prefixed usernames`() = runTest {
        val members = listOf(MemberWithChat(1L, 456L, "user_123", "NoUsername", MemberRole.MEMBER, null))
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(members)

        val result = membersController.getMembers(chatId)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("• NoUsername"))
        assertTrue(!result.message.contains("@user_123"))
    }

    @Test
    fun `getMembers should return Success with empty message when no members`() = runTest {
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(emptyList())

        val result = membersController.getMembers(chatId)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Немає зареєстрованих учасників"))
    }

    @Test
    fun `getMembers should return Error on service failure`() = runTest {
        val error = DatabaseException("Connection lost")
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.failure(error)

        val result = membersController.getMembers(chatId)

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains(error.userMessage))
    }
}
