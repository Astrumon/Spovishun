package presentation.controller

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.service.AutoRegisterService
import com.ua.astrumon.domain.service.MemberService
import com.ua.astrumon.presentation.controller.MembersController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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
    private val member = Member(1L, chatId, userId, username, firstName, null)

    @BeforeTest
    fun setup() {
        clearAllMocks()
        membersController = MembersController(memberService, autoRegisterService)
        coEvery { autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), any()) } returns ResultContainer.success(member)
    }

    @Test
    fun `getMembers should pass userRole to ensureUserRegistered`() = runTest {
        val memberWithDifferentIds = Member(99L, chatId, userId, username, firstName, null)
        coEvery { memberService.getAllMembers() } returns ResultContainer.success(emptyList())

        membersController.getMembers(chatId, memberWithDifferentIds, MemberRole.ADMIN)

        coVerify { autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, MemberRole.ADMIN) }
    }

    @Test
    fun `getMembers should return Success with formatted list and role badges`() = runTest {
        val members = listOf(
            Member(1L, chatId, 456L, "alice", "Alice", null, role = MemberRole.ADMIN),
            Member(2L, chatId, 789L, "bob", "Bob", null, role = MemberRole.MODERATOR),
            Member(3L, chatId, 111L, "charlie", "Charlie", null, role = MemberRole.MEMBER)
        )
        coEvery { memberService.getAllMembers() } returns ResultContainer.success(members)

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
        val members = listOf(Member(1L, chatId, 456L, "user_123", "NoUsername", null))
        coEvery { memberService.getAllMembers() } returns ResultContainer.success(members)

        val result = membersController.getMembers(chatId, member, MemberRole.MEMBER)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("• NoUsername"))
        assertTrue(!result.message.contains("@user_123"))
    }

    @Test
    fun `getMembers should return Success with empty message when no members`() = runTest {
        coEvery { memberService.getAllMembers() } returns ResultContainer.success(emptyList())

        val result = membersController.getMembers(chatId, member, MemberRole.MEMBER)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Немає зареєстрованих учасників"))
    }

    @Test
    fun `getMembers should return Error on service failure`() = runTest {
        val error = DatabaseException("Connection lost")
        coEvery { memberService.getAllMembers() } returns ResultContainer.failure(error)

        val result = membersController.getMembers(chatId, member, MemberRole.MEMBER)

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains(error.userMessage))
    }

    @Test
    fun `getMembers should pass ADMIN role to ensureUserRegistered when userRole is ADMIN`() = runTest {
        val adminMember = Member(1L, chatId, userId, "admin_alice", "Admin Alice", null, role = MemberRole.ADMIN)
        coEvery { memberService.getAllMembers() } returns ResultContainer.success(listOf(adminMember))
        coEvery { autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), eq(MemberRole.ADMIN)) } returns ResultContainer.success(adminMember)

        membersController.getMembers(chatId, adminMember, MemberRole.ADMIN)

        coVerify { autoRegisterService.ensureUserRegistered(chatId, userId, "admin_alice", "Admin Alice", MemberRole.ADMIN) }
    }
}
