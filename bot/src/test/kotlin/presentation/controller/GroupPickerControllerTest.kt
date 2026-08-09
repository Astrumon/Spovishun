package presentation.controller

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.MemberChat
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.model.MemberWithChat
import com.ua.astrumon.domain.bot.service.GroupService
import com.ua.astrumon.domain.bot.service.GroupWithMembers
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.controller.GroupPickerController
import com.ua.astrumon.presentation.controller.PickerListing
import com.ua.astrumon.presentation.controller.PickerOption
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import presentation.testMessagesProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The picker half of what used to be one GroupControllerTest, split alongside its controller (spovishun-172). */
class GroupPickerControllerTest {
    private val groupService: GroupService = mockk()
    private val memberService: MemberService = mockk()
    private lateinit var groupController: GroupPickerController

    private val chatId = 123L
    private val userId = 456L
    private val username = "alice"
    private val firstName = "Alice"
    private val adminMemberWithChat = MemberWithChat(1L, userId, username, firstName, MemberRole.ADMIN, null)

    @BeforeTest
    fun setup() {
        clearAllMocks()
        groupController = GroupPickerController(groupService, memberService, testMessagesProvider())
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns true
        coEvery { memberService.hasAdminAccess(chatId, userId) } returns true
    }

    // --- Inline-picker listings (spovishun-123) ---

    private val bob = MemberWithChat(2L, 789L, "bob", "Bob", MemberRole.MEMBER, null)

    @Test
    fun `groupsForModeratorPicker should return options for each group`() = runTest {
        val groups = listOf(
            GroupWithMembers(10L, chatId, "devs", "devs", emptyList()),
            GroupWithMembers(20L, chatId, "qa", "qa", emptyList()),
        )
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(groups)

        val result = groupController.groupsForModeratorPicker(chatId, userId)

        assertTrue(result is PickerListing.Show)
        assertEquals(listOf(PickerOption(10L, "devs"), PickerOption(20L, "qa")), result.options)
    }

    @Test
    fun `groupsForModeratorPicker should label a group button with its icon`() = runTest {
        val groups = listOf(
            GroupWithMembers(10L, chatId, "devs", "devs", emptyList(), icon = "🔥"),
            GroupWithMembers(20L, chatId, "qa", "qa", emptyList()),
        )
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(groups)

        val result = groupController.groupsForModeratorPicker(chatId, userId)

        assertTrue(result is PickerListing.Show)
        assertEquals(listOf(PickerOption(10L, "🔥 devs"), PickerOption(20L, "qa")), result.options)
    }

    @Test
    fun `groupsForModeratorPicker should reject when caller is regular member`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns false

        val result = groupController.groupsForModeratorPicker(chatId, userId)

        assertTrue(result is PickerListing.Reject)
        assertTrue(result.response is CommandResponse.AccessDenied)
        coVerify(exactly = 0) { groupService.getAllGroupsWithMembers(any()) }
    }

    @Test
    fun `groupsForModeratorPicker should reject on service failure`() = runTest {
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.failure(DatabaseException("down"))

        val result = groupController.groupsForModeratorPicker(chatId, userId)

        assertTrue(result is PickerListing.Reject)
        assertTrue(result.response is CommandResponse.Error)
    }

    @Test
    fun `chatMembersForAdminPicker should map members to options`() = runTest {
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(listOf(adminMemberWithChat, bob))

        val result = groupController.chatMembersForAdminPicker(chatId, userId)

        assertTrue(result is PickerListing.Show)
        assertEquals(listOf(PickerOption(userId, "@alice"), PickerOption(789L, "@bob")), result.options)
    }

    @Test
    fun `chatMembersForAdminPicker should reject when caller is not admin`() = runTest {
        coEvery { memberService.hasAdminAccess(chatId, userId) } returns false

        val result = groupController.chatMembersForAdminPicker(chatId, userId)

        assertTrue(result is PickerListing.Reject)
        assertTrue(result.response is CommandResponse.AccessDenied)
    }

    @Test
    fun `groupMembersForPicker should return only members of that group`() = runTest {
        val group = GroupWithMembers(10L, chatId, "devs", "devs", listOf("bob"))
        coEvery { groupService.getGroupById(chatId, 10L) } returns ResultContainer.success(group)
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(listOf(adminMemberWithChat, bob))

        val result = groupController.groupMembersForPicker(chatId, userId, 10L)

        assertTrue(result is PickerListing.Show)
        assertEquals(listOf(PickerOption(789L, "@bob")), result.options)
    }

    @Test
    fun `groupMembersForPicker should reject when group id is unknown`() = runTest {
        coEvery { groupService.getGroupById(chatId, 999L) } returns ResultContainer.failure(ResourceNotFoundException("Group", "999"))

        val result = groupController.groupMembersForPicker(chatId, userId, 999L)

        assertTrue(result is PickerListing.Reject)
        assertTrue(result.response is CommandResponse.NotFound)
    }

    // --- Inline-picker actions by id (spovishun-123) ---

    @Test
    fun `deleteGroupById should delete resolved group`() = runTest {
        val group = GroupWithMembers(10L, chatId, "devs", "devs", emptyList())
        coEvery { groupService.getGroupById(chatId, 10L) } returns ResultContainer.success(group)
        coEvery { groupService.deleteGroup(chatId, "devs") } returns ResultContainer.success(Unit)

        val result = groupController.deleteGroupById(chatId, userId, 10L)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("видалена"))
        coVerify(exactly = 1) { groupService.deleteGroup(chatId, "devs") }
    }

    @Test
    fun `deleteGroupById should return NotFound for unknown id`() = runTest {
        coEvery { groupService.getGroupById(chatId, 999L) } returns ResultContainer.failure(ResourceNotFoundException("Group", "999"))

        val result = groupController.deleteGroupById(chatId, userId, 999L)

        assertTrue(result is CommandResponse.NotFound)
        coVerify(exactly = 0) { groupService.deleteGroup(any(), any()) }
    }

    @Test
    fun `deleteGroupById should return AccessDenied for regular member`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns false

        val result = groupController.deleteGroupById(chatId, userId, 10L)

        assertTrue(result is CommandResponse.AccessDenied)
        coVerify(exactly = 0) { groupService.getGroupById(any(), any()) }
    }

    @Test
    fun `addUserToGroupById should add resolved member to resolved group`() = runTest {
        val group = GroupWithMembers(10L, chatId, "devs", "devs", emptyList())
        coEvery { groupService.getGroupById(chatId, 10L) } returns ResultContainer.success(group)
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(listOf(bob))
        coEvery { groupService.addMemberToGroup(chatId, "devs", "bob") } returns ResultContainer.success(Unit)

        val result = groupController.addUserToGroupById(chatId, userId, 10L, 789L)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("додано"))
        assertTrue(result.message.contains("@bob"))
    }

    @Test
    fun `addUserToGroupById should return NotFound when member id is unknown`() = runTest {
        val group = GroupWithMembers(10L, chatId, "devs", "devs", emptyList())
        coEvery { groupService.getGroupById(chatId, 10L) } returns ResultContainer.success(group)
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(emptyList())

        val result = groupController.addUserToGroupById(chatId, userId, 10L, 999L)

        assertTrue(result is CommandResponse.NotFound)
        coVerify(exactly = 0) { groupService.addMemberToGroup(any(), any(), any()) }
    }

    @Test
    fun `removeUserFromGroupById should remove resolved member`() = runTest {
        val group = GroupWithMembers(10L, chatId, "devs", "devs", listOf("bob"))
        coEvery { groupService.getGroupById(chatId, 10L) } returns ResultContainer.success(group)
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(listOf(bob))
        coEvery { groupService.removeMemberFromGroup(chatId, "devs", "bob") } returns ResultContainer.success(Unit)

        val result = groupController.removeUserFromGroupById(chatId, userId, 10L, 789L)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("видалено"))
    }

    @Test
    fun `grantRoleById should grant role to resolved member`() = runTest {
        val bobChat = MemberChat(2L, chatId, MemberRole.MEMBER, null)
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(listOf(bob))
        coEvery { memberService.setMemberRole(chatId, 789L, MemberRole.MODERATOR) } returns
            ResultContainer.success(bobChat.copy(role = MemberRole.MODERATOR))

        val result = groupController.grantRoleById(chatId, userId, 789L, MemberRole.MODERATOR)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("moderator"))
        assertTrue(result.message.contains("@bob"))
    }

    @Test
    fun `grantRoleById should return NotFound when member id is unknown`() = runTest {
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(emptyList())

        val result = groupController.grantRoleById(chatId, userId, 999L, MemberRole.ADMIN)

        assertTrue(result is CommandResponse.NotFound)
        coVerify(exactly = 0) { memberService.setMemberRole(any(), any(), any()) }
    }

    @Test
    fun `grantRoleById should return AccessDenied when caller is not admin`() = runTest {
        coEvery { memberService.hasAdminAccess(chatId, userId) } returns false

        val result = groupController.grantRoleById(chatId, userId, 789L, MemberRole.ADMIN)

        assertTrue(result is CommandResponse.AccessDenied)
        coVerify(exactly = 0) { memberService.setMemberRole(any(), any(), any()) }
    }
}
