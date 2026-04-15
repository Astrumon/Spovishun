package presentation.controller

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.model.MemberWithChat
import com.ua.astrumon.domain.service.AutoRegisterService
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

    private val autoRegisterService: AutoRegisterService = mockk()
    private lateinit var registrationController: RegistrationController

    private val chatId = 123L
    private val userId = 456L
    private val memberWithChat = MemberWithChat(1L, userId, "alice", "Alice", MemberRole.MEMBER, null)

    @BeforeTest
    fun setup() {
        clearAllMocks()
        registrationController = RegistrationController(autoRegisterService)
        coEvery { autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), any()) } returns ResultContainer.success(memberWithChat)
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
        coEvery { autoRegisterService.isUserRegistered(chatId, "alice") } returns false

        val result = registrationController.register(chatId, userId, "alice", "Alice", MemberRole.MEMBER)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("зареєстровані"))
        coVerify { autoRegisterService.ensureUserRegistered(chatId, userId, "alice", "Alice", MemberRole.MEMBER) }
    }

    @Test
    fun `register should return Success with already registered message when duplicate`() = runTest {
        coEvery { autoRegisterService.isUserRegistered(chatId, "alice") } returns true

        val result = registrationController.register(chatId, userId, "alice", "Alice", MemberRole.MEMBER)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("вже зареєстровані"))
        coVerify { autoRegisterService.ensureUserRegistered(chatId, userId, "alice", "Alice", MemberRole.MEMBER) }
    }

    @Test
    fun `register should include admin role text for admin users`() = runTest {
        coEvery { autoRegisterService.isUserRegistered(chatId, "alice") } returns false

        val result = registrationController.register(chatId, userId, "alice", "Alice", MemberRole.ADMIN)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("адміністратор"))
        coVerify { autoRegisterService.ensureUserRegistered(chatId, userId, "alice", "Alice", MemberRole.ADMIN) }
    }

    @Test
    fun `register should return Error when ensureUserRegistered fails`() = runTest {
        coEvery { autoRegisterService.isUserRegistered(chatId, "alice") } returns false
        coEvery { autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), any()) } returns
            ResultContainer.failure(DatabaseException("connection timeout"))

        val result = registrationController.register(chatId, userId, "alice", "Alice", MemberRole.MEMBER)

        assertTrue(result is CommandResponse.Error)
    }
}
