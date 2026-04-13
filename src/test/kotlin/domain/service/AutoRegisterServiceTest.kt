package domain.service

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.exception.ValidationException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Chat
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.model.MemberWithChat
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

    private val chatId = 123L
    private val userId = 456L
    private val username = "alice"
    private val firstName = "Alice"
    private val userRole = MemberRole.MEMBER

    private val memberWithChat = MemberWithChat(
        id = 1L, userId = userId, username = username, firstName = firstName,
        role = userRole, joinedAt = null,
    )

    @BeforeTest
    fun setup() {
        clearAllMocks()
        autoRegisterService = AutoRegisterService(memberService, chatService)
        coEvery { chatService.ensureChat(any(), any(), any()) } returns ResultContainer.success(
            Chat(chatId, null, null, Clock.System.now())
        )
    }

    @Test
    fun `ensureUserRegistered should return existing member when already registered in chat`() = runTest {
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.success(memberWithChat)

        val result = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        assertTrue(result.isSuccess)
        assertEquals(memberWithChat, result.getOrThrow())
        coVerify(exactly = 0) { memberService.createMember(any(), any(), any(), any()) }
    }

    @Test
    fun `ensureUserRegistered should create new member when not in this chat`() = runTest {
        val notFoundError = ResourceNotFoundException("Member", username)
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.failure(notFoundError)
        coEvery { memberService.createMember(chatId, userId, username, firstName, role = userRole) } returns ResultContainer.success(memberWithChat)

        val result = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        assertTrue(result.isSuccess)
        assertEquals(memberWithChat, result.getOrThrow())
        coVerify { memberService.createMember(chatId, userId, username, firstName, role = userRole) }
    }

    @Test
    fun `ensureUserRegistered should return validation error when userId is invalid`() = runTest {
        val result = autoRegisterService.ensureUserRegistered(chatId, -1L, username, firstName, userRole)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ValidationException)
        coVerify(exactly = 0) { memberService.getMemberWithChatByUsername(any(), any()) }
        coVerify(exactly = 0) { memberService.createMember(any(), any(), any(), any()) }
    }

    @Test
    fun `ensureUserRegistered should return failure when member creation fails with non-duplicate error`() = runTest {
        val notFoundError = ResourceNotFoundException("Member", username)
        val dbError = DatabaseException("DB down")
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.failure(notFoundError)
        coEvery { memberService.createMember(chatId, userId, username, firstName, role = userRole) } returns ResultContainer.failure(dbError)

        val result = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DatabaseException)
    }

    @Test
    fun `ensureUserRegistered should return failure when member lookup fails with non-notfound error`() = runTest {
        val error = DatabaseException("Database error")
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.failure(error)

        val result = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DatabaseException)
        coVerify(exactly = 0) { memberService.createMember(any(), any(), any(), any()) }
    }

    @Test
    fun `ensureUserRegistered should register admin with ADMIN role`() = runTest {
        val adminRole = MemberRole.ADMIN
        val adminMemberWithChat = memberWithChat.copy(role = adminRole)
        val notFoundError = ResourceNotFoundException("Member", username)
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.failure(notFoundError)
        coEvery { memberService.createMember(chatId, userId, username, firstName, role = adminRole) } returns ResultContainer.success(adminMemberWithChat)

        val result = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, adminRole)

        assertTrue(result.isSuccess)
        assertEquals(MemberRole.ADMIN, result.getOrThrow().role)
        coVerify { memberService.createMember(chatId, userId, username, firstName, role = adminRole) }
    }

    @Test
    fun `isUserRegistered should return true when member exists in chat`() = runTest {
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.success(memberWithChat)

        assertTrue(autoRegisterService.isUserRegistered(chatId, username))
    }

    @Test
    fun `isUserRegistered should return false when member does not exist`() = runTest {
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.failure(
            ResourceNotFoundException("Member", username)
        )

        assertFalse(autoRegisterService.isUserRegistered(chatId, username))
    }

    @Test
    fun `isUserRegistered should return false when lookup fails`() = runTest {
        coEvery { memberService.getMemberWithChatByUsername(chatId, username) } returns ResultContainer.failure(
            DatabaseException("DB error")
        )

        assertFalse(autoRegisterService.isUserRegistered(chatId, username))
    }
}
