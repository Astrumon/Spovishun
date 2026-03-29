package domain.service

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.exception.ValidationException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.model.Chat
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.service.AutoRegisterService
import com.ua.astrumon.domain.service.ChatService
import com.ua.astrumon.domain.service.MemberService
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoRegisterServiceTest {

    private val memberService: MemberService = mockk()
    private val chatService: ChatService = mockk()
    private lateinit var autoRegisterService: AutoRegisterService

    @BeforeTest
    fun setup() {
        clearAllMocks()
        autoRegisterService = AutoRegisterService(memberService, chatService)
        coEvery { chatService.ensureChat(any(), any(), any()) } returns ResultContainer.success(
            Chat(123L, null, null, Clock.System.now())
        )
    }

    @Test
    fun `ensureUserRegistered should return existing member when user already exists`() = runTest {
        // Given
        val chatId = 123L
        val userId = 456L
        val username = "alice"
        val firstName = "Alice"
        val userRole = MemberRole.MEMBER
        val existingMember = Member(1L, chatId, userId, username, firstName, null, userRole)

        coEvery { memberService.getMemberByUsername(username) } returns ResultContainer.success(existingMember)

        // When
        val result = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(existingMember, result.getOrThrow())
        coVerify { memberService.getMemberByUsername(username) }
        coVerify(exactly = 0) { memberService.createMember(any(), any(), any(), any()) }
    }

    @Test
    fun `ensureUserRegistered should create new member when user does not exist`() = runTest {
        // Given
        val chatId = 123L
        val userId = 456L
        val username = "alice"
        val firstName = "Alice"
        val userRole = MemberRole.MEMBER
        val newMember = Member(1L, chatId, userId, username, firstName, null, userRole)
        val notFoundError = ResourceNotFoundException("Member", username)

        coEvery { memberService.getMemberByUsername(username) } returns ResultContainer.failure(notFoundError)
        coEvery { memberService.createMember(chatId, userId, username, firstName) } returns ResultContainer.success(newMember)

        // When
        val result = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(newMember, result.getOrThrow())
        coVerify { memberService.getMemberByUsername(username) }
        coVerify { memberService.createMember(chatId, userId, username, firstName) }
    }

    @Test
    fun `ensureUserRegistered should return validation error when userId is invalid`() = runTest {
        // Given
        val chatId = 123L
        val userId = -1L
        val username = "alice"
        val firstName = "Alice"
        val userRole = MemberRole.MEMBER

        // When
        val result = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ValidationException)
        assertEquals("Cannot register user with invalid userId: -1", result.exceptionOrNull()?.message)
        coVerify(exactly = 0) { memberService.getMemberByUsername(any()) }
        coVerify(exactly = 0) { memberService.createMember(any(), any(), any(), any()) }
    }

    @Test
    fun `ensureUserRegistered should return failure when member creation fails`() = runTest {
        // Given
        val chatId = 123L
        val userId = 456L
        val username = "alice"
        val firstName = "Alice"
        val userRole = MemberRole.MEMBER
        val notFoundError = ResourceNotFoundException("Member", username)

        coEvery { memberService.getMemberByUsername(username) } returns ResultContainer.failure(notFoundError)
        coEvery { memberService.createMember(chatId, userId, username, firstName) } returns ResultContainer.failure(
            DuplicateResourceException("Member", username)
        )

        // When
        val result = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is DuplicateResourceException)
        assertEquals("Member with identifier '$username' already exists", exception.message)
        coVerify { memberService.getMemberByUsername(username) }
        coVerify { memberService.createMember(chatId, userId, username, firstName) }
    }

    @Test
    fun `ensureUserRegistered should return failure when member lookup fails`() = runTest {
        // Given
        val chatId = 123L
        val userId = 456L
        val username = "alice"
        val firstName = "Alice"
        val userRole = MemberRole.MEMBER
        val error = DatabaseException("Database error")

        coEvery { memberService.getMemberByUsername(username) } returns ResultContainer.failure(error)

        // When
        val result = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        // Then
        assertTrue(result.isFailure)
        coVerify { memberService.getMemberByUsername(username) }
        coVerify(exactly = 0) { memberService.createMember(any(), any(), any(), any()) }
    }

    @Test
    fun `isUserRegistered should return true when member exists`() = runTest {
        // Given
        val username = "alice"
        val member = Member(1L, 123L, 456L, username, "Alice", null)

        coEvery { memberService.getMemberByUsername(username) } returns ResultContainer.success(member)

        // When
        val result = autoRegisterService.isUserRegistered(username)

        // Then
        assertTrue(result)
        coVerify { memberService.getMemberByUsername(username) }
    }

    @Test
    fun `isUserRegistered should return false when member does not exist`() = runTest {
        // Given
        val username = "alice"

        coEvery { memberService.getMemberByUsername(username) } returns ResultContainer.failure(
            ResourceNotFoundException("Member", username)
        )

        // When
        val result = autoRegisterService.isUserRegistered(username)

        // Then
        assertFalse(result)
        coVerify { memberService.getMemberByUsername(username) }
    }

    @Test
    fun `isUserRegistered should return false when member lookup fails`() = runTest {
        // Given
        val username = "alice"

        coEvery { memberService.getMemberByUsername(username) } returns ResultContainer.failure(
            DatabaseException("Database error")
        )

        // When
        val result = autoRegisterService.isUserRegistered(username)

        // Then
        assertFalse(result)
        coVerify { memberService.getMemberByUsername(username) }
    }

    @Test
    fun `ensureUserRegistered should create new admin member when admin does not exist`() = runTest {
        // Given
        val chatId = 123L
        val userId = 456L
        val username = "admin_alice"
        val firstName = "Admin Alice"
        val userRole = MemberRole.ADMIN
        val newAdminMember = Member(1L, chatId, userId, username, firstName, null, userRole)
        val notFoundError = ResourceNotFoundException("Member", username)

        coEvery { memberService.getMemberByUsername(username) } returns ResultContainer.failure(notFoundError)
        coEvery { memberService.createMember(chatId, userId, username, firstName, role = userRole) } returns ResultContainer.success(newAdminMember)

        // When
        val result = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(newAdminMember, result.getOrThrow())
        assertEquals(MemberRole.ADMIN, result.getOrThrow().role)
        coVerify { memberService.getMemberByUsername(username) }
        coVerify { memberService.createMember(chatId, userId, username, firstName, role = userRole) }
    }

    @Test
    fun `ensureUserRegistered should return existing admin member when admin already exists`() = runTest {
        // Given
        val chatId = 123L
        val userId = 456L
        val username = "admin_bob"
        val firstName = "Admin Bob"
        val userRole = MemberRole.ADMIN
        val existingAdminMember = Member(1L, chatId, userId, username, firstName, null, userRole)

        coEvery { memberService.getMemberByUsername(username) } returns ResultContainer.success(existingAdminMember)

        // When
        val result = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(existingAdminMember, result.getOrThrow())
        assertEquals(MemberRole.ADMIN, result.getOrThrow().role)
        coVerify { memberService.getMemberByUsername(username) }
        coVerify(exactly = 0) { memberService.createMember(any(), any(), any(), any()) }
    }

}
