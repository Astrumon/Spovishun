package domain.service

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.repository.MemberRepository
import com.ua.astrumon.domain.service.MemberService
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.Instant
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemberServiceTest {

    private val memberRepository: MemberRepository = mockk()
    private lateinit var memberService: MemberService

    @BeforeTest
    fun setup() {
        clearAllMocks()
        memberService = MemberService(memberRepository)
    }

    @Test
    fun `createMember should return success when username is unique`() = runTest {
        // Given
        val chatId = 123L
        val userId = 456L
        val username = "alice"
        val firstName = "Alice"
        val joinedAt = Instant.fromEpochMilliseconds(1234567890)
        val expectedMember = Member(1L, chatId, userId, username, firstName, joinedAt)

        coEvery { memberRepository.findByUsername(username) } returns ResultContainer.success(null)
        coEvery { 
            memberRepository.save(chatId, userId, username, firstName, any()) 
        } returns ResultContainer.success(expectedMember)

        // When
        val result = memberService.createMember(chatId, userId, username, firstName)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedMember, result.getOrThrow())
        coVerify { memberRepository.findByUsername(username) }
        coVerify { memberRepository.save(chatId, userId, username, firstName, any()) }
    }

    @Test
    fun `createMember should return failure when username already exists`() = runTest {
        // Given
        val chatId = 123L
        val userId = 456L
        val username = "alice"
        val firstName = "Alice"
        val existingMember = Member(1L, chatId, 789L, username, "Old Alice", null)

        coEvery { memberRepository.findByUsername(username) } returns ResultContainer.success(existingMember)

        // When
        val result = memberService.createMember(chatId, userId, username, firstName)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is DuplicateResourceException)
        assertEquals("Member with identifier '$username' already exists", exception.message)
        coVerify { memberRepository.findByUsername(username) }
        coVerify(exactly = 0) { memberRepository.save(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `createMember should propagate repository error`() = runTest {
        // Given
        val chatId = 123L
        val userId = 456L
        val username = "alice"
        val firstName = "Alice"
        val error = DatabaseException("Database error")

        coEvery { memberRepository.findByUsername(username) } returns ResultContainer.failure(error)

        // When
        val result = memberService.createMember(chatId, userId, username, firstName)

        // Then
        assertTrue(result.isFailure)
        assertEquals("Database operation failed: Database error", result.exceptionOrNull()?.message)
        coVerify { memberRepository.findByUsername(username) }
    }

    @Test
    fun `getMemberByUsername should return member when found`() = runTest {
        // Given
        val username = "alice"
        val member = Member(1L, 123L, 456L, username, "Alice", null)

        coEvery { memberRepository.findByUsername(username) } returns ResultContainer.success(member)

        // When
        val result = memberService.getMemberByUsername(username)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(member, result.getOrThrow())
        coVerify { memberRepository.findByUsername(username) }
    }

    @Test
    fun `getMemberByUsername should return failure when member not found`() = runTest {
        // Given
        val username = "alice"

        coEvery { memberRepository.findByUsername(username) } returns ResultContainer.success(null)

        // When
        val result = memberService.getMemberByUsername(username)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
        coVerify { memberRepository.findByUsername(username) }
    }

    @Test
    fun `getMemberByUsername should propagate repository error`() = runTest {
        // Given
        val username = "alice"
        val error = DatabaseException("Database error")

        coEvery { memberRepository.findByUsername(username) } returns ResultContainer.failure(error)

        // When
        val result = memberService.getMemberByUsername(username)

        // Then
        assertTrue(result.isFailure)
        assertEquals("Database operation failed: Database error", result.exceptionOrNull()?.message)
        coVerify { memberRepository.findByUsername(username) }
    }

    @Test
    fun `updateMemberUsername should return same member when username unchanged`() = runTest {
        // Given
        val currentUsername = "alice"
        val newUsername = "alice"
        val member = Member(1L, 123L, 456L, currentUsername, "Alice", null)

        coEvery { memberRepository.findByUsername(currentUsername) } returns ResultContainer.success(member)

        // When
        val result = memberService.updateMemberUsername(currentUsername, newUsername)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(member, result.getOrThrow())
        coVerify { memberRepository.findByUsername(currentUsername) }
        coVerify(exactly = 0) { memberRepository.save(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `updateMemberUsername should return success when new username is unique`() = runTest {
        // Given
        val currentUsername = "alice"
        val newUsername = "alice_new"
        val currentMember = Member(1L, 123L, 456L, currentUsername, "Alice", null)
        val updatedMember = Member(1L, 123L, 456L, newUsername, "Alice", null)

        coEvery { memberRepository.findByUsername(currentUsername) } returns ResultContainer.success(currentMember)
        coEvery { memberRepository.findByUsername(newUsername) } returns ResultContainer.success(null)
        coEvery { 
            memberRepository.save(123L, 456L, newUsername, "Alice", null) 
        } returns ResultContainer.success(updatedMember)

        // When
        val result = memberService.updateMemberUsername(currentUsername, newUsername)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(updatedMember, result.getOrThrow())
        coVerify { memberRepository.findByUsername(currentUsername) }
        coVerify { memberRepository.findByUsername(newUsername) }
        coVerify { memberRepository.save(123L, 456L, newUsername, "Alice", null) }
    }

    @Test
    fun `updateMemberUsername should return failure when new username already exists`() = runTest {
        // Given
        val currentUsername = "alice"
        val newUsername = "bob"
        val currentMember = Member(1L, 123L, 456L, currentUsername, "Alice", null)
        val existingMember = Member(2L, 123L, 789L, newUsername, "Bob", null)

        coEvery { memberRepository.findByUsername(currentUsername) } returns ResultContainer.success(currentMember)
        coEvery { memberRepository.findByUsername(newUsername) } returns ResultContainer.success(existingMember)

        // When
        val result = memberService.updateMemberUsername(currentUsername, newUsername)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is DuplicateResourceException)
        assertEquals("Member with identifier '$newUsername' already exists", exception.message)
        coVerify { memberRepository.findByUsername(currentUsername) }
        coVerify { memberRepository.findByUsername(newUsername) }
        coVerify(exactly = 0) { memberRepository.save(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `updateMemberUsername should return failure when current member not found`() = runTest {
        // Given
        val currentUsername = "alice"
        val newUsername = "alice_new"
        val error = ResourceNotFoundException("Member", currentUsername)

        coEvery { memberRepository.findByUsername(currentUsername) } returns ResultContainer.failure(error)

        // When
        val result = memberService.updateMemberUsername(currentUsername, newUsername)

        // Then
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
        coVerify { memberRepository.findByUsername(currentUsername) }
        coVerify(exactly = 0) { memberRepository.findByUsername(newUsername) }
        coVerify(exactly = 0) { memberRepository.save(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `getAllMembers should return success with members list`() = runTest {
        // Given
        val members = listOf(
            Member(1L, 123L, 456L, "alice", "Alice", null),
            Member(2L, 123L, 789L, "bob", "Bob", null)
        )

        coEvery { memberRepository.findAll() } returns ResultContainer.success(members)

        // When
        val result = memberService.getAllMembers()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(members, result.getOrThrow())
        coVerify { memberRepository.findAll() }
    }

    @Test
    fun `getAllMembers should return empty list when no members exist`() = runTest {
        // Given
        coEvery { memberRepository.findAll() } returns ResultContainer.success(emptyList())

        // When
        val result = memberService.getAllMembers()

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        coVerify { memberRepository.findAll() }
    }

    @Test
    fun `getAllMembers should propagate repository error`() = runTest {
        // Given
        val error = DatabaseException("Database error")
        coEvery { memberRepository.findAll() } returns ResultContainer.failure(error)

        // When
        val result = memberService.getAllMembers()

        // Then
        assertTrue(result.isFailure)
        assertEquals("Database operation failed: Database error", result.exceptionOrNull()?.message)
        coVerify { memberRepository.findAll() }
    }

    @Test
    fun `getMemberByChatAndUserId should return member when found`() = runTest {
        // Given
        val chatId = 123L
        val userId = 456L
        val member = Member(1L, chatId, userId, "alice", "Alice", null)

        coEvery { memberRepository.findByChatIdAndUserId(chatId, userId) } returns ResultContainer.success(member)

        // When
        val result = memberService.getMemberByChatAndUserId(chatId, userId)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(member, result.getOrThrow())
        coVerify { memberRepository.findByChatIdAndUserId(chatId, userId) }
    }

    @Test
    fun `getMemberByChatAndUserId should return failure when member not found`() = runTest {
        // Given
        val chatId = 123L
        val userId = 456L

        coEvery { memberRepository.findByChatIdAndUserId(chatId, userId) } returns ResultContainer.success(null)

        // When
        val result = memberService.getMemberByChatAndUserId(chatId, userId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
        coVerify { memberRepository.findByChatIdAndUserId(chatId, userId) }
    }

    @Test
    fun `setMemberRole should return updated member on success`() = runTest {
        // Given
        val chatId = 123L
        val userId = 456L
        val member = Member(1L, chatId, userId, "alice", "Alice", null, role = MemberRole.MEMBER)
        val updated = member.copy(role = MemberRole.MODERATOR)

        coEvery { memberRepository.findByChatIdAndUserId(chatId, userId) } returns ResultContainer.success(member)
        coEvery { memberRepository.updateRole(chatId, userId, MemberRole.MODERATOR) } returns ResultContainer.success(updated)

        // When
        val result = memberService.setMemberRole(chatId, userId, MemberRole.MODERATOR)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(MemberRole.MODERATOR, result.getOrThrow().role)
        coVerify { memberRepository.updateRole(chatId, userId, MemberRole.MODERATOR) }
    }

    @Test
    fun `setMemberRole should return failure when member not found`() = runTest {
        // Given
        val chatId = 123L
        val userId = 999L

        coEvery { memberRepository.findByChatIdAndUserId(chatId, userId) } returns ResultContainer.success(null)

        // When
        val result = memberService.setMemberRole(chatId, userId, MemberRole.ADMIN)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
        coVerify(exactly = 0) { memberRepository.updateRole(any(), any(), any()) }
    }

    @Test
    fun `setMemberRole should promote member to admin successfully`() = runTest {
        // Given
        val chatId = 123L
        val userId = 456L
        val member = Member(1L, chatId, userId, "alice", "Alice", null, role = MemberRole.MEMBER)
        val adminMember = member.copy(role = MemberRole.ADMIN)

        coEvery { memberRepository.findByChatIdAndUserId(chatId, userId) } returns ResultContainer.success(member)
        coEvery { memberRepository.updateRole(chatId, userId, MemberRole.ADMIN) } returns ResultContainer.success(adminMember)

        // When
        val result = memberService.setMemberRole(chatId, userId, MemberRole.ADMIN)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(MemberRole.ADMIN, result.getOrThrow().role)
        assertEquals("alice", result.getOrThrow().username)
        coVerify { memberRepository.updateRole(chatId, userId, MemberRole.ADMIN) }
    }
}
