package presentation.controller

import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.service.AutoRegisterService
import com.ua.astrumon.domain.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.controller.RegistrationController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class RegistrationControllerTest {

    private val memberService: MemberService = mockk()
    private val autoRegisterService: AutoRegisterService = mockk()
    private lateinit var registrationController: RegistrationController

    private val chatId = 123L
    private val userId = 456L
    private val member = Member(1L, chatId, userId, "alice", "Alice", null)

    @BeforeTest
    fun setup() {
        clearAllMocks()
        registrationController = RegistrationController(memberService, autoRegisterService)
        coEvery { autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), any()) } returns ResultContainer.success(member)
    }

    // --- start ---

    @Test
    fun `start should return Success with welcome message`() = runTest {
        val result = registrationController.start(chatId, userId, "alice", "Alice", MemberRole.MEMBER)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Spovishun активний"))
    }

    @Test
    fun `start should register trigger user with given role`() = runTest {
        registrationController.start(chatId, userId, "alice", "Alice", MemberRole.MEMBER)

        coVerify { autoRegisterService.ensureUserRegistered(chatId, userId, "alice", "Alice", MemberRole.MEMBER) }
    }

    @Test
    fun `start should register trigger user as admin when userRole is ADMIN`() = runTest {
        registrationController.start(chatId, userId, "alice", "Alice", MemberRole.ADMIN)

        coVerify { autoRegisterService.ensureUserRegistered(chatId, userId, "alice", "Alice", MemberRole.ADMIN) }
    }

    // --- ensureUserRegistered ---

    @Test
    fun `ensureUserRegistered should delegate to autoRegisterService`() = runTest {
        registrationController.ensureUserRegistered(chatId, 789L, "admin", "Admin", MemberRole.ADMIN)

        coVerify { autoRegisterService.ensureUserRegistered(chatId, 789L, "admin", "Admin", MemberRole.ADMIN) }
    }

    // --- register ---

    @Test
    fun `register should return Success when registration succeeds`() = runTest {
        val newMember = Member(1L, chatId, userId, "alice", "Alice", null)
        coEvery { memberService.createMember(chatId, userId, "alice", "Alice", MemberRole.MEMBER) } returns ResultContainer.success(newMember)

        val result = registrationController.register(chatId, userId, "alice", "Alice", MemberRole.MEMBER)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("зареєстровані"))
    }

    @Test
    fun `register should return Success with already registered message when duplicate`() = runTest {
        coEvery { memberService.createMember(chatId, userId, "alice", "Alice", MemberRole.MEMBER) } returns
            ResultContainer.failure(DuplicateResourceException("Member", "alice"))

        val result = registrationController.register(chatId, userId, "alice", "Alice", MemberRole.MEMBER)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("вже зареєстровані"))
    }

    @Test
    fun `register should include admin role text for admin users`() = runTest {
        val adminMember = Member(1L, chatId, userId, "alice", "Alice", null, MemberRole.ADMIN)
        coEvery { memberService.createMember(chatId, userId, "alice", "Alice", MemberRole.ADMIN) } returns ResultContainer.success(adminMember)

        val result = registrationController.register(chatId, userId, "alice", "Alice", MemberRole.ADMIN)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("адміністратор"))
    }
}
