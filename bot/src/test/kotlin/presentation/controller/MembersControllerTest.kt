package presentation.controller

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.model.MemberWithChat
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.controller.MembersController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import presentation.testMessagesProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class MembersControllerTest {
    private val memberService: MemberService = mockk()
    private val autoRegisterService: AutoRegisterService = mockk()
    private lateinit var membersController: MembersController

    private val chatId = 123L
    private val userId = 456L
    private val username = "alice"
    private val firstName = "Alice"
    private val member = Member(1L, userId, username, firstName)
    private val memberWithChat = MemberWithChat(1L, userId, username, firstName, MemberRole.MEMBER, null)

    @BeforeTest
    fun setup() {
        clearAllMocks()
        membersController = MembersController(memberService, autoRegisterService, testMessagesProvider())
        coEvery { autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), any()) } returns
            ResultContainer.success(memberWithChat)
    }

    @Test
    fun `getMembers should pass userRole to ensureUserRegistered`() = runTest {
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(emptyList())

        membersController.getMembers(chatId, member, MemberRole.ADMIN)

        coVerify { autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, MemberRole.ADMIN) }
    }

    @Test
    fun `getMembers should return Success with formatted list and role badges`() = runTest {
        val members = listOf(
            MemberWithChat(1L, 456L, "alice", "Alice", MemberRole.ADMIN, null),
            MemberWithChat(2L, 789L, "bob", "Bob", MemberRole.MODERATOR, null),
            MemberWithChat(3L, 111L, "charlie", "Charlie", MemberRole.MEMBER, null),
        )
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(members)

        val result = membersController.getMembers(chatId, member, MemberRole.MEMBER)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Зареєстровані учасники:"))
        assertTrue(result.message.contains("@alice \uD83D\uDD10"))
        assertTrue(result.message.contains("@bob \uD83D\uDEE1"))
        assertTrue(result.message.contains("@charlie"))
        assertTrue(result.message.contains("Всього: 3 учасників"))
        coVerify { autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, MemberRole.MEMBER) }
    }

    @Test
    fun `getMembers should show firstName for user_ prefixed usernames`() = runTest {
        val members = listOf(MemberWithChat(1L, 456L, "user_123", "NoUsername", MemberRole.MEMBER, null))
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(members)

        val result = membersController.getMembers(chatId, member, MemberRole.MEMBER)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("• NoUsername"))
        assertTrue(!result.message.contains("@user_123"))
    }

    @Test
    fun `getMembers should return Success with empty message when no members`() = runTest {
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(emptyList())

        val result = membersController.getMembers(chatId, member, MemberRole.MEMBER)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Немає зареєстрованих учасників"))
    }

    @Test
    fun `getMembers should return Error on service failure`() = runTest {
        val error = DatabaseException("Connection lost")
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.failure(error)

        val result = membersController.getMembers(chatId, member, MemberRole.MEMBER)

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains(error.userMessage))
    }

    @Test
    fun `getMembers should pass ADMIN role to ensureUserRegistered when userRole is ADMIN`() = runTest {
        val adminMember = Member(1L, userId, "admin_alice", "Admin Alice")
        val adminMemberWithChat = MemberWithChat(1L, userId, "admin_alice", "Admin Alice", MemberRole.ADMIN, null)
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(listOf(adminMemberWithChat))
        coEvery { autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), eq(MemberRole.ADMIN)) } returns
            ResultContainer.success(adminMemberWithChat)

        membersController.getMembers(chatId, adminMember, MemberRole.ADMIN)

        coVerify { autoRegisterService.ensureUserRegistered(chatId, userId, "admin_alice", "Admin Alice", MemberRole.ADMIN) }
    }
}
