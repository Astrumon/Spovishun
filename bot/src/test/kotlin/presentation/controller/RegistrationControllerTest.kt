package presentation.controller

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.BirthDate
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.model.MemberWithChat
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.domain.bot.service.BirthdayService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.controller.RegistrationController
import com.ua.astrumon.presentation.controller.RegistrationRequest
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import presentation.testMessagesProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegistrationControllerTest {
    private val autoRegisterService: AutoRegisterService = mockk()
    private val birthdayService: BirthdayService = mockk()
    private lateinit var registrationController: RegistrationController

    private val chatId = 123L
    private val userId = 456L
    private val memberWithChat = MemberWithChat(1L, userId, "alice", "Alice", MemberRole.MEMBER, null)
    private val member = Member(1L, userId, "alice", "Alice", BirthDate(1, 1))
    private val request = RegistrationRequest(chatId, userId, "alice", "Alice", MemberRole.MEMBER)
    private val adminRequest = request.copy(userRole = MemberRole.ADMIN)

    @BeforeTest
    fun setup() {
        clearAllMocks()
        registrationController = RegistrationController(autoRegisterService, birthdayService, testMessagesProvider())
        coEvery { autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), any()) } returns
            ResultContainer.success(memberWithChat)
    }

    /**
     * The role reaches the service as a supplier now (spovishun-172), so asserting *which* role was
     * registered means capturing that supplier and invoking it.
     */
    private suspend fun assertRegistered(expected: RegistrationRequest) {
        val resolveRole = slot<suspend () -> MemberRole>()
        coVerify {
            autoRegisterService.ensureUserRegistered(
                expected.chatId,
                expected.userId,
                expected.username,
                expected.firstName,
                capture(resolveRole),
            )
        }
        assertEquals(expected.userRole, resolveRole.captured.invoke())
    }

    // --- start ---

    @Test
    fun `start should return Success with welcome message`() = runTest {
        val result = registrationController.start(request)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Spovishun на місці"))
    }

    @Test
    fun `start should register trigger user with given role`() = runTest {
        registrationController.start(request)

        assertRegistered(request)
    }

    @Test
    fun `start should register trigger user as admin when userRole is ADMIN`() = runTest {
        registrationController.start(adminRequest)

        assertRegistered(adminRequest)
    }

    // --- ensureUserRegistered ---

    @Test
    fun `ensureUserRegistered should delegate to autoRegisterService`() = runTest {
        val adminSync = RegistrationRequest(chatId, 789L, "admin", "Admin", MemberRole.ADMIN)

        registrationController.ensureUserRegistered(adminSync)

        assertRegistered(adminSync)
    }

    // --- register ---

    @Test
    fun `register should return Success when registration succeeds`() = runTest {
        coEvery { autoRegisterService.isUserRegistered(chatId, "alice") } returns ResultContainer.success(false)

        val result = registrationController.register(request)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("зареєстровані"))
        assertRegistered(request)
    }

    @Test
    fun `register should return Success with already registered message when duplicate`() = runTest {
        coEvery { autoRegisterService.isUserRegistered(chatId, "alice") } returns ResultContainer.success(true)

        val result = registrationController.register(request)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("вже зареєстровані"))
        assertRegistered(request)
    }

    @Test
    fun `register should include admin role text for admin users`() = runTest {
        coEvery { autoRegisterService.isUserRegistered(chatId, "alice") } returns ResultContainer.success(false)

        val result = registrationController.register(adminRequest)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("адміністратор"))
        assertRegistered(adminRequest)
    }

    @Test
    fun `register should return Error when ensureUserRegistered fails`() = runTest {
        coEvery { autoRegisterService.isUserRegistered(chatId, "alice") } returns ResultContainer.success(false)
        coEvery { autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), any()) } returns
            ResultContainer.failure(DatabaseException("connection timeout"))

        val result = registrationController.register(request)

        assertTrue(result is CommandResponse.Error)
    }

    @Test
    fun `register should return Error when registration lookup fails`() = runTest {
        coEvery { autoRegisterService.isUserRegistered(chatId, "alice") } returns
            ResultContainer.failure(DatabaseException("connection timeout"))

        val result = registrationController.register(request)

        assertTrue(result is CommandResponse.Error)
        coVerify(exactly = 0) { autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), any()) }
    }

    // --- register with $b birthday token ---

    @Test
    fun `register should save birthday and append confirmation when token is valid`() = runTest {
        coEvery { autoRegisterService.isUserRegistered(chatId, "alice") } returns ResultContainer.success(false)
        coEvery { birthdayService.setBirthday(userId, BirthDate(1, 1)) } returns ResultContainer.success(member)

        val result = registrationController.register(request, "01.01")

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("зареєстровані"))
        assertTrue(result.message.contains("День народження збережено"))
        assertTrue(result.message.contains("01.01"))
        coVerify { birthdayService.setBirthday(userId, BirthDate(1, 1)) }
    }

    @Test
    fun `register should not touch birthday service when token is absent`() = runTest {
        coEvery { autoRegisterService.isUserRegistered(chatId, "alice") } returns ResultContainer.success(false)

        registrationController.register(request)

        coVerify(exactly = 0) { birthdayService.setBirthday(any(), any()) }
    }

    @Test
    fun `register should return Error and skip registration when birthday token is invalid`() = runTest {
        val result = registrationController.register(request, "abc")

        assertTrue(result is CommandResponse.Error)
        coVerify(exactly = 0) { autoRegisterService.isUserRegistered(any(), any()) }
        coVerify(exactly = 0) { autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { birthdayService.setBirthday(any(), any()) }
    }

    @Test
    fun `register should return Error when birthday token is empty`() = runTest {
        val result = registrationController.register(request, "")

        assertTrue(result is CommandResponse.Error)
        coVerify(exactly = 0) { autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `register should keep Success with warning when birthday save fails`() = runTest {
        coEvery { autoRegisterService.isUserRegistered(chatId, "alice") } returns ResultContainer.success(false)
        coEvery { birthdayService.setBirthday(userId, BirthDate(1, 1)) } returns
            ResultContainer.failure(DatabaseException("connection timeout"))

        val result = registrationController.register(request, "01.01")

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("зареєстровані"))
        assertTrue(result.message.contains("не вдалося"))
    }
}
