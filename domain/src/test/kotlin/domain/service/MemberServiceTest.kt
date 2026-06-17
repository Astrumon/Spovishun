package domain.service

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.model.MemberChat
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.model.MemberWithChat
import com.ua.astrumon.domain.repository.MemberChatRepository
import com.ua.astrumon.domain.repository.MemberRepository
import com.ua.astrumon.domain.service.MemberService
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemberServiceTest {
    private val memberRepository: MemberRepository = mockk()
    private val memberChatRepository: MemberChatRepository = mockk()
    private lateinit var memberService: MemberService

    private val chatId = 123L
    private val userId = 456L
    private val memberId = 1L
    private val username = "alice"
    private val firstName = "Alice"

    private val member = Member(id = memberId, userId = userId, username = username, firstName = firstName)
    private val memberChat = MemberChat(memberId = memberId, chatId = chatId, role = MemberRole.MEMBER, joinedAt = null)
    private val memberWithChat =
        MemberWithChat(
            id = memberId,
            userId = userId,
            username = username,
            firstName = firstName,
            role = MemberRole.MEMBER,
            joinedAt = null,
        )

    @BeforeTest
    fun setup() {
        clearAllMocks()
        memberService = MemberService(memberRepository, memberChatRepository)
    }

    // ── createMember ──────────────────────────────────────────────────────────

    @Test
    fun `createMember should return success when user is new to this chat`() = runTest {
        coEvery { memberRepository.saveOrUpdate(userId, username, firstName) } returns ResultContainer.success(member)
        coEvery { memberChatRepository.existsByMemberIdAndChatId(memberId, chatId) } returns ResultContainer.success(false)
        coEvery { memberChatRepository.save(memberId, chatId, MemberRole.MEMBER, any()) } returns ResultContainer.success(memberChat)

        val result = memberService.createMember(chatId, userId, username, firstName)

        assertTrue(result.isSuccess)
        assertEquals(username, result.getOrThrow().username)
        coVerify { memberRepository.saveOrUpdate(userId, username, firstName) }
        coVerify { memberChatRepository.save(memberId, chatId, MemberRole.MEMBER, any()) }
    }

    @Test
    fun `createMember should return DuplicateResourceException when already in this chat`() = runTest {
        coEvery { memberRepository.saveOrUpdate(userId, username, firstName) } returns ResultContainer.success(member)
        coEvery { memberChatRepository.existsByMemberIdAndChatId(memberId, chatId) } returns ResultContainer.success(true)

        val result = memberService.createMember(chatId, userId, username, firstName)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DuplicateResourceException)
        coVerify(exactly = 0) { memberChatRepository.save(any(), any(), any(), any()) }
    }

    @Test
    fun `createMember should propagate repository error`() = runTest {
        val error = DatabaseException("Database error")
        coEvery { memberRepository.saveOrUpdate(userId, username, firstName) } returns ResultContainer.failure(error)

        val result = memberService.createMember(chatId, userId, username, firstName)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DatabaseException)
    }

    // ── getMemberByUsername ───────────────────────────────────────────────────

    @Test
    fun `getMemberByUsername should return member when found`() = runTest {
        coEvery { memberRepository.findByUsername(username) } returns ResultContainer.success(member)

        val result = memberService.getMemberByUsername(username)

        assertTrue(result.isSuccess)
        assertEquals(member, result.getOrThrow())
    }

    @Test
    fun `getMemberByUsername should return failure when not found`() = runTest {
        coEvery { memberRepository.findByUsername(username) } returns ResultContainer.success(null)

        val result = memberService.getMemberByUsername(username)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `getMemberByUsername should propagate repository error`() = runTest {
        val error = DatabaseException("Database error")
        coEvery { memberRepository.findByUsername(username) } returns ResultContainer.failure(error)

        val result = memberService.getMemberByUsername(username)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DatabaseException)
    }

    // ── getMemberChatByUserId ─────────────────────────────────────────────────

    @Test
    fun `getMemberChatByUserId should return memberChat when found`() = runTest {
        coEvery { memberRepository.findByUserId(userId) } returns ResultContainer.success(member)
        coEvery { memberChatRepository.findByMemberIdAndChatId(memberId, chatId) } returns ResultContainer.success(memberChat)

        val result = memberService.getMemberChatByUserId(chatId, userId)

        assertTrue(result.isSuccess)
        assertEquals(memberChat, result.getOrThrow())
    }

    @Test
    fun `getMemberChatByUserId should return failure when member not found`() = runTest {
        coEvery { memberRepository.findByUserId(userId) } returns ResultContainer.success(null)

        val result = memberService.getMemberChatByUserId(chatId, userId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `getMemberChatByUserId should return failure when not in this chat`() = runTest {
        coEvery { memberRepository.findByUserId(userId) } returns ResultContainer.success(member)
        coEvery { memberChatRepository.findByMemberIdAndChatId(memberId, chatId) } returns ResultContainer.success(null)

        val result = memberService.getMemberChatByUserId(chatId, userId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    // ── setMemberRole ─────────────────────────────────────────────────────────

    @Test
    fun `setMemberRole should return updated memberChat on success`() = runTest {
        val updatedChat = memberChat.copy(role = MemberRole.MODERATOR)
        coEvery { memberRepository.findByUserId(userId) } returns ResultContainer.success(member)
        coEvery { memberChatRepository.findByMemberIdAndChatId(memberId, chatId) } returns ResultContainer.success(memberChat)
        coEvery { memberChatRepository.updateRole(memberId, chatId, MemberRole.MODERATOR) } returns ResultContainer.success(updatedChat)

        val result = memberService.setMemberRole(chatId, userId, MemberRole.MODERATOR)

        assertTrue(result.isSuccess)
        assertEquals(MemberRole.MODERATOR, result.getOrThrow().role)
        coVerify { memberChatRepository.updateRole(memberId, chatId, MemberRole.MODERATOR) }
    }

    @Test
    fun `setMemberRole should return failure when member not found`() = runTest {
        coEvery { memberRepository.findByUserId(userId) } returns ResultContainer.success(null)

        val result = memberService.setMemberRole(chatId, userId, MemberRole.ADMIN)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
        coVerify(exactly = 0) { memberChatRepository.updateRole(any(), any(), any()) }
    }

    // ── updateMemberUsername ──────────────────────────────────────────────────

    @Test
    fun `updateMemberUsername should return same member when username unchanged`() = runTest {
        coEvery { memberRepository.findByUsername(username) } returns ResultContainer.success(member)

        val result = memberService.updateMemberUsername(username, username)

        assertTrue(result.isSuccess)
        assertEquals(member, result.getOrThrow())
        coVerify(exactly = 0) { memberRepository.saveOrUpdate(any(), any(), any()) }
    }

    @Test
    fun `updateMemberUsername should call saveOrUpdate when username changes`() = runTest {
        val newUsername = "alice_new"
        val updatedMember = member.copy(username = newUsername)
        coEvery { memberRepository.findByUsername(username) } returns ResultContainer.success(member)
        coEvery { memberRepository.saveOrUpdate(userId, newUsername, firstName) } returns ResultContainer.success(updatedMember)

        val result = memberService.updateMemberUsername(username, newUsername)

        assertTrue(result.isSuccess)
        assertEquals(newUsername, result.getOrThrow().username)
        coVerify { memberRepository.saveOrUpdate(userId, newUsername, firstName) }
    }

    // ── getMemberWithChatByUsername ───────────────────────────────────────────

    @Test
    fun `getMemberWithChatByUsername should return memberWithChat when found`() = runTest {
        coEvery { memberRepository.findMemberWithChatByChatIdAndUsername(chatId, username) } returns
            ResultContainer.success(memberWithChat)

        val result = memberService.getMemberWithChatByUsername(chatId, username)

        assertTrue(result.isSuccess)
        assertEquals(memberWithChat, result.getOrThrow())
    }

    @Test
    fun `getMemberWithChatByUsername should return failure when not found`() = runTest {
        coEvery { memberRepository.findMemberWithChatByChatIdAndUsername(chatId, username) } returns ResultContainer.success(null)

        val result = memberService.getMemberWithChatByUsername(chatId, username)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ResourceNotFoundException)
    }

    @Test
    fun `getMemberWithChatByUsername should propagate repository error`() = runTest {
        val error = DatabaseException("DB error")
        coEvery { memberRepository.findMemberWithChatByChatIdAndUsername(chatId, username) } returns ResultContainer.failure(error)

        val result = memberService.getMemberWithChatByUsername(chatId, username)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DatabaseException)
    }

    // ── getAllMembersInChat ────────────────────────────────────────────────────

    @Test
    fun `getAllMembersInChat should return list via single JOIN query`() = runTest {
        coEvery { memberRepository.findAllMembersWithChatByChatId(chatId) } returns ResultContainer.success(listOf(memberWithChat))

        val result = memberService.getAllMembersInChat(chatId)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals(username, result.getOrThrow().first().username)
        assertEquals(MemberRole.MEMBER, result.getOrThrow().first().role)
        coVerify(exactly = 0) { memberChatRepository.findAllByChatId(any()) }
    }

    @Test
    fun `getAllMembersInChat should return empty list when no members`() = runTest {
        coEvery { memberRepository.findAllMembersWithChatByChatId(chatId) } returns ResultContainer.success(emptyList())

        val result = memberService.getAllMembersInChat(chatId)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    // ── hasAdminAccess / hasModeratorAccess ───────────────────────────────────

    @Test
    fun `hasAdminAccess should return true for ADMIN role`() = runTest {
        val adminChat = memberChat.copy(role = MemberRole.ADMIN)
        coEvery { memberRepository.findByUserId(userId) } returns ResultContainer.success(member)
        coEvery { memberChatRepository.findByMemberIdAndChatId(memberId, chatId) } returns ResultContainer.success(adminChat)

        assertTrue(memberService.hasAdminAccess(chatId, userId))
    }

    @Test
    fun `hasAdminAccess should return false for MEMBER role`() = runTest {
        coEvery { memberRepository.findByUserId(userId) } returns ResultContainer.success(member)
        coEvery { memberChatRepository.findByMemberIdAndChatId(memberId, chatId) } returns ResultContainer.success(memberChat)

        assertTrue(!memberService.hasAdminAccess(chatId, userId))
    }

    @Test
    fun `hasModeratorAccess should return true for MODERATOR role`() = runTest {
        val modChat = memberChat.copy(role = MemberRole.MODERATOR)
        coEvery { memberRepository.findByUserId(userId) } returns ResultContainer.success(member)
        coEvery { memberChatRepository.findByMemberIdAndChatId(memberId, chatId) } returns ResultContainer.success(modChat)

        assertTrue(memberService.hasModeratorAccess(chatId, userId))
    }

    @Test
    fun `hasModeratorAccess should return false when user not in chat`() = runTest {
        coEvery { memberRepository.findByUserId(userId) } returns ResultContainer.success(null)

        assertTrue(!memberService.hasModeratorAccess(chatId, userId))
    }
}
