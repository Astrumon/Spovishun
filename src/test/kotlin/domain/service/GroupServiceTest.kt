package domain.service

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Group
import com.ua.astrumon.domain.repository.GroupMemberRepository
import com.ua.astrumon.domain.repository.GroupRepository
import com.ua.astrumon.domain.service.GroupService
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupServiceTest {

    private val groupRepository: GroupRepository = mockk()
    private val groupMemberRepository: GroupMemberRepository = mockk()
    private lateinit var groupService: GroupService

    @BeforeTest
    fun setup() {
        clearAllMocks()
        groupService = GroupService(groupRepository, groupMemberRepository)
    }

    @Test
    fun `getAllGroupsWithMembers should return groups with members when successful`() = runTest {
        // Given
        val chatId = 123L
        val groups = listOf(
            Group(1L, chatId, "devs", emptyList()),
            Group(2L, chatId, "admins", emptyList())
        )
        val members1 = listOf("alice", "bob")
        val members2 = listOf("charlie")

        coEvery { groupRepository.getAllGroups(chatId) } returns ResultContainer.success(groups)
        coEvery { groupMemberRepository.getGroupMembers(chatId, "devs") } returns ResultContainer.success(members1)
        coEvery { groupMemberRepository.getGroupMembers(chatId, "admins") } returns ResultContainer.success(members2)

        // When
        val result = groupService.getAllGroupsWithMembers(chatId)

        // Then
        assertTrue(result.isSuccess)
        val groupWithMembers = result.getOrThrow()
        assertEquals(2, groupWithMembers.size)
        
        val devsGroup = groupWithMembers.find { it.key == "devs" }
        assertEquals(members1, devsGroup?.members)

        val adminsGroup = groupWithMembers.find { it.key == "admins" }
        assertEquals(members2, adminsGroup?.members)

        coVerify { groupRepository.getAllGroups(chatId) }
        coVerify { groupMemberRepository.getGroupMembers(chatId, "devs") }
        coVerify { groupMemberRepository.getGroupMembers(chatId, "admins") }
    }

    @Test
    fun `getAllGroupsWithMembers should return empty list when no groups exist`() = runTest {
        // Given
        val chatId = 123L
        coEvery { groupRepository.getAllGroups(chatId) } returns ResultContainer.success(emptyList())

        // When
        val result = groupService.getAllGroupsWithMembers(chatId)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
        coVerify { groupRepository.getAllGroups(chatId) }
    }

    @Test
    fun `getAllGroupsWithMembers should propagate repository error`() = runTest {
        // Given
        val chatId = 123L
        val error = ResourceNotFoundException("Group", "chatId")
        coEvery { groupRepository.getAllGroups(chatId) } returns ResultContainer.failure(error)

        // When
        val result = groupService.getAllGroupsWithMembers(chatId)

        // Then
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
        coVerify { groupRepository.getAllGroups(chatId) }
    }

    @Test
    fun `createGroup should return success when repository succeeds`() = runTest {
        // Given
        val chatId = 123L
        val groupName = "devs"
        val expectedGroup = Group(1L, chatId, groupName, emptyList())
        coEvery { groupRepository.createGroup(chatId, groupName) } returns ResultContainer.success(expectedGroup)

        // When
        val result = groupService.createGroup(chatId, groupName)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedGroup, result.getOrThrow())
        coVerify { groupRepository.createGroup(chatId, groupName) }
    }

    @Test
    fun `createGroup should propagate repository error`() = runTest {
        // Given
        val chatId = 123L
        val groupName = "devs"
        val error = DatabaseException("Database error")
        coEvery { groupRepository.createGroup(chatId, groupName) } returns ResultContainer.failure(error)

        // When
        val result = groupService.createGroup(chatId, groupName)

        // Then
        assertTrue(result.isFailure)
        assertEquals("Database operation failed: Database error", result.exceptionOrNull()?.message)
        coVerify { groupRepository.createGroup(chatId, groupName) }
    }

    @Test
    fun `deleteGroup should return success when repository succeeds`() = runTest {
        // Given
        val chatId = 123L
        val groupKey = "devs"
        coEvery { groupRepository.deleteGroup(chatId, groupKey) } returns ResultContainer.success(Unit)

        // When
        val result = groupService.deleteGroup(chatId, groupKey)

        // Then
        assertTrue(result.isSuccess)
        coVerify { groupRepository.deleteGroup(chatId, groupKey) }
    }

    @Test
    fun `deleteGroup should propagate repository error`() = runTest {
        // Given
        val chatId = 123L
        val groupKey = "devs"
        val error = ResourceNotFoundException("Group", groupKey)
        coEvery { groupRepository.deleteGroup(chatId, groupKey) } returns ResultContainer.failure(error)

        // When
        val result = groupService.deleteGroup(chatId, groupKey)

        // Then
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
        coVerify { groupRepository.deleteGroup(chatId, groupKey) }
    }

    @Test
    fun `addMemberToGroup should return success when repository succeeds`() = runTest {
        // Given
        val chatId = 123L
        val groupKey = "devs"
        val username = "alice"
        coEvery { groupMemberRepository.addMemberToGroup(chatId, groupKey, username) } returns ResultContainer.success(Unit)

        // When
        val result = groupService.addMemberToGroup(chatId, groupKey, username)

        // Then
        assertTrue(result.isSuccess)
        coVerify { groupMemberRepository.addMemberToGroup(chatId, groupKey, username) }
    }

    @Test
    fun `addMemberToGroup should propagate repository error`() = runTest {
        // Given
        val chatId = 123L
        val groupKey = "devs"
        val username = "alice"
        val error = ResourceNotFoundException("Group", groupKey)
        coEvery { groupMemberRepository.addMemberToGroup(chatId, groupKey, username) } returns ResultContainer.failure(error)

        // When
        val result = groupService.addMemberToGroup(chatId, groupKey, username)

        // Then
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
        coVerify { groupMemberRepository.addMemberToGroup(chatId, groupKey, username) }
    }

    @Test
    fun `removeMemberFromGroup should return success when repository succeeds`() = runTest {
        // Given
        val chatId = 123L
        val groupKey = "devs"
        val username = "alice"
        coEvery { groupMemberRepository.removeMemberFromGroup(chatId, groupKey, username) } returns ResultContainer.success(Unit)

        // When
        val result = groupService.removeMemberFromGroup(chatId, groupKey, username)

        // Then
        assertTrue(result.isSuccess)
        coVerify { groupMemberRepository.removeMemberFromGroup(chatId, groupKey, username) }
    }

    @Test
    fun `removeMemberFromGroup should propagate repository error`() = runTest {
        // Given
        val chatId = 123L
        val groupKey = "devs"
        val username = "alice"
        val error = ResourceNotFoundException("GroupMember", username)
        coEvery { groupMemberRepository.removeMemberFromGroup(chatId, groupKey, username) } returns ResultContainer.failure(error)

        // When
        val result = groupService.removeMemberFromGroup(chatId, groupKey, username)

        // Then
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
        coVerify { groupMemberRepository.removeMemberFromGroup(chatId, groupKey, username) }
    }

    @Test
    fun `getGroupByKey should return group with members when successful`() = runTest {
        // Given
        val chatId = 123L
        val groupKey = "devs"
        val group = Group(1L, chatId, groupKey, emptyList())
        val members = listOf("alice", "bob")

        coEvery { groupRepository.findGroupByKey(chatId, groupKey) } returns ResultContainer.success(group)
        coEvery { groupMemberRepository.getGroupMembers(chatId, groupKey) } returns ResultContainer.success(members)

        // When
        val result = groupService.getGroupByKey(chatId, groupKey)

        // Then
        assertTrue(result.isSuccess)
        val groupWithMembers = result.getOrThrow()
        assertEquals(group.id, groupWithMembers.id)
        assertEquals(chatId, groupWithMembers.chatId)
        assertEquals(groupKey, groupWithMembers.key)
        assertEquals(groupKey, groupWithMembers.name)
        assertEquals(members, groupWithMembers.members)

        coVerify { groupRepository.findGroupByKey(chatId, groupKey) }
        coVerify { groupMemberRepository.getGroupMembers(chatId, groupKey) }
    }

    @Test
    fun `getGroupByKey should propagate repository error when group not found`() = runTest {
        // Given
        val chatId = 123L
        val groupKey = "devs"
        val error = ResourceNotFoundException("Group", groupKey)
        coEvery { groupRepository.findGroupByKey(chatId, groupKey) } returns ResultContainer.failure(error)

        // When
        val result = groupService.getGroupByKey(chatId, groupKey)

        // Then
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
        coVerify { groupRepository.findGroupByKey(chatId, groupKey) }
        coVerify(exactly = 0) { groupMemberRepository.getGroupMembers(any(), any()) }
    }

    @Test
    fun `getGroupByKey should propagate members repository error`() = runTest {
        // Given
        val chatId = 123L
        val groupKey = "devs"
        val group = Group(1L, chatId, groupKey, emptyList())
        val error = DatabaseException("Database error")

        coEvery { groupRepository.findGroupByKey(chatId, groupKey) } returns ResultContainer.success(group)
        coEvery { groupMemberRepository.getGroupMembers(chatId, groupKey) } returns ResultContainer.failure(error)

        // When
        val result = groupService.getGroupByKey(chatId, groupKey)

        // Then
        assertTrue(result.isFailure)
        assertEquals("Database operation failed: Database error", result.exceptionOrNull()?.message)
        coVerify { groupRepository.findGroupByKey(chatId, groupKey) }
        coVerify { groupMemberRepository.getGroupMembers(chatId, groupKey) }
    }
}
