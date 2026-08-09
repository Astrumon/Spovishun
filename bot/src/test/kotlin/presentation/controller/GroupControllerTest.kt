package presentation.controller

import com.ua.astrumon.common.exception.BusinessException
import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.exception.ValidationException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.Group
import com.ua.astrumon.domain.bot.model.GroupSettingsPatch
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.domain.bot.model.MemberChat
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.model.MemberWithChat
import com.ua.astrumon.domain.bot.model.Patch
import com.ua.astrumon.domain.bot.model.PingMark
import com.ua.astrumon.domain.bot.service.GroupService
import com.ua.astrumon.domain.bot.service.GroupWithMembers
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.controller.GroupParam
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import presentation.testMessagesProvider
import presentation.ukMessages
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupControllerTest {
    private val groupService: GroupService = mockk()
    private val memberService: MemberService = mockk()
    private lateinit var groupController: GroupController

    private val chatId = 123L
    private val userId = 456L
    private val username = "alice"
    private val firstName = "Alice"
    private val adminMemberWithChat = MemberWithChat(1L, userId, username, firstName, MemberRole.ADMIN, null)

    @BeforeTest
    fun setup() {
        clearAllMocks()
        groupController = GroupController(groupService, memberService, testMessagesProvider())
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns true
        coEvery { memberService.hasAdminAccess(chatId, userId) } returns true
    }

    // --- getGroups tests ---

    @Test
    fun `getGroups should return success with formatted list and badges`() = runTest {
        val moderatorWithChat = MemberWithChat(2L, 789L, "bob", "Bob", MemberRole.MODERATOR, null)
        val regularWithChat = MemberWithChat(3L, 111L, "charlie", "Charlie", MemberRole.MEMBER, null)
        val groups = listOf(
            GroupWithMembers(1L, chatId, "devs", "devs", listOf("alice", "bob", "charlie")),
            GroupWithMembers(2L, chatId, "qa", "qa", emptyList()),
        )
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(groups)
        coEvery { memberService.getMemberWithChatByUsername(chatId, "alice") } returns ResultContainer.success(adminMemberWithChat)
        coEvery { memberService.getMemberWithChatByUsername(chatId, "bob") } returns ResultContainer.success(moderatorWithChat)
        coEvery { memberService.getMemberWithChatByUsername(chatId, "charlie") } returns ResultContainer.success(regularWithChat)

        val result = groupController.getGroups(chatId)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Групи:"))
        assertTrue(result.message.contains("@alice \uD83D\uDD10"))
        assertTrue(result.message.contains("@bob \uD83D\uDEE1"))
        assertTrue(result.message.contains("@charlie"))
        assertTrue(result.message.contains("—"))
    }

    @Test
    fun `getGroups should prefix a group with its icon`() = runTest {
        val groups = listOf(
            GroupWithMembers(1L, chatId, "devs", "devs", emptyList(), icon = "🔥"),
            GroupWithMembers(2L, chatId, "qa", "qa", emptyList()),
        )
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(groups)

        val result = groupController.getGroups(chatId)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("🔥 devs"))
        assertTrue(result.message.contains("<b>qa</b>"))
    }

    @Test
    fun `getGroups should handle member lookup failure gracefully`() = runTest {
        val groups = listOf(GroupWithMembers(1L, chatId, "devs", "devs", listOf("unknown")))
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(groups)
        coEvery { memberService.getMemberWithChatByUsername(chatId, "unknown") } returns ResultContainer.failure(
            ResourceNotFoundException("Member", "unknown"),
        )

        val result = groupController.getGroups(chatId)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("@unknown"))
    }

    @Test
    fun `getGroups should return success with empty message when no groups`() = runTest {
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(emptyList())

        val result = groupController.getGroups(chatId)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Немає груп"))
    }

    @Test
    fun `getGroups should return error on service failure`() = runTest {
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.failure(DatabaseException("Connection lost"))

        val result = groupController.getGroups(chatId)

        assertTrue(result is CommandResponse.Error)
    }

    // --- createGroup tests ---

    @Test
    fun `createGroup should return success when created`() = runTest {
        val group = Group(1L, chatId, "devs", emptyList())
        coEvery { groupService.createGroup(chatId, "devs") } returns ResultContainer.success(group)

        val result = groupController.createGroup(chatId, userId, listOf("Devs"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("devs"))
        assertTrue(result.message.contains("створена"))
    }

    @Test
    fun `createGroup should leave every setting untouched when no parameters are given`() = runTest {
        coEvery { groupService.createGroup(chatId, "devs", GroupSettingsPatch()) } returns
            ResultContainer.success(Group(1L, chatId, "devs", emptyList()))

        groupController.createGroup(chatId, userId, listOf("devs"))

        coVerify(exactly = 1) { groupService.createGroup(chatId, "devs", GroupSettingsPatch()) }
    }

    @Test
    fun `createGroup should return AccessDenied when caller is regular member`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns false

        val result = groupController.createGroup(chatId, userId, listOf("devs"))

        assertTrue(result is CommandResponse.AccessDenied)
        coVerify(exactly = 0) { groupService.createGroup(any(), any(), any()) }
    }

    @Test
    fun `createGroup should return error when no args`() = runTest {
        val result = groupController.createGroup(chatId, userId, emptyList())

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains("/newgroup"))
    }

    @Test
    fun `createGroup should return error when group already exists`() = runTest {
        coEvery { groupService.createGroup(chatId, "devs") } returns ResultContainer.failure(
            DuplicateResourceException("Group", "devs"),
        )

        val result = groupController.createGroup(chatId, userId, listOf("devs"))

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains("вже існує"))
    }

    // --- createGroup parameter tests (spovishun-182) ---

    /**
     * Stubbing the exact patch is the assertion: a looser `any()` would pass just as happily if the
     * controller dropped the parameter on the way to the service, which is the whole failure mode.
     */
    private fun expectCreate(patch: GroupSettingsPatch) {
        coEvery { groupService.createGroup(chatId, "devs", patch) } returns
            ResultContainer.success(Group(1L, chatId, "devs", emptyList()))
    }

    /** Every rejection has the same two halves: an error back, and nothing written. */
    private fun assertRejected(
        result: CommandResponse,
        expectedMessage: String,
    ) {
        assertTrue(result is CommandResponse.Error)
        assertEquals(expectedMessage, result.message)
        coVerify(exactly = 0) { groupService.createGroup(any(), any(), any()) }
    }

    @Test
    fun `createGroup should store the icon when icon parameter is given`() = runTest {
        expectCreate(GroupSettingsPatch(icon = Patch.Value("🔥")))

        val result = groupController.createGroup(chatId, userId, listOf("devs", "\$icon=🔥"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("🔥"))
        coVerify(exactly = 1) { groupService.createGroup(chatId, "devs", GroupSettingsPatch(icon = Patch.Value("🔥"))) }
    }

    @Test
    fun `createGroup should store the ping mark when mark parameter is given`() = runTest {
        val patch = GroupSettingsPatch(pingMark = Patch.Value(PingMark.Custom("🦀")))
        expectCreate(patch)

        val result = groupController.createGroup(chatId, userId, listOf("devs", "\$mark=🦀"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("🦀"))
        coVerify(exactly = 1) { groupService.createGroup(chatId, "devs", patch) }
    }

    @Test
    fun `createGroup should hide the ping mark when mark is off`() = runTest {
        val patch = GroupSettingsPatch(pingMark = Patch.Value(PingMark.Hidden))
        expectCreate(patch)

        groupController.createGroup(chatId, userId, listOf("devs", "\$mark=off"))

        coVerify(exactly = 1) { groupService.createGroup(chatId, "devs", patch) }
    }

    @Test
    fun `createGroup should apply icon and mark in one call`() = runTest {
        val patch = GroupSettingsPatch(icon = Patch.Value("🔥"), pingMark = Patch.Value(PingMark.Custom("🦀")))
        expectCreate(patch)

        val result = groupController.createGroup(chatId, userId, listOf("devs", "\$icon=🔥", "\$mark=🦀"))

        assertTrue(result is CommandResponse.Success)
        coVerify(exactly = 1) { groupService.createGroup(chatId, "devs", patch) }
    }

    @Test
    fun `createGroup should reject a token that is not a parameter`() = runTest {
        val result = groupController.createGroup(chatId, userId, listOf("devs", "qa"))

        assertRejected(result, ukMessages.group.paramError.notAParameter("qa"))
    }

    @Test
    fun `createGroup should reject a known parameter written without the separator`() = runTest {
        val result = groupController.createGroup(chatId, userId, listOf("devs", "\$icon", "🔥"))

        assertRejected(result, ukMessages.group.paramError.missingSeparator(GroupParam.ICON.flag))
    }

    @Test
    fun `createGroup should reject an unknown parameter`() = runTest {
        val result = groupController.createGroup(chatId, userId, listOf("devs", "\$colour=red"))

        assertRejected(result, ukMessages.group.paramError.unknown("\$colour", GroupParam.supported()))
    }

    @Test
    fun `createGroup should reject a duplicated parameter`() = runTest {
        val result = groupController.createGroup(chatId, userId, listOf("devs", "\$icon=🔥", "\$icon=⚡"))

        assertRejected(result, ukMessages.group.paramError.duplicate(GroupParam.ICON.flag))
    }

    @Test
    fun `createGroup should reject a parameter with an empty value`() = runTest {
        val result = groupController.createGroup(chatId, userId, listOf("devs", "\$icon="))

        assertRejected(result, ukMessages.group.paramError.emptyValue(GroupParam.ICON.flag))
    }

    @Test
    fun `createGroup should reject name parameter because the name is positional`() = runTest {
        val result = groupController.createGroup(chatId, userId, listOf("devs", "\$name=qa"))

        assertRejected(result, ukMessages.group.nameParamNotAllowed)
    }

    @Test
    fun `createGroup should reject an icon that is not a single emoji`() = runTest {
        val result = groupController.createGroup(chatId, userId, listOf("devs", "\$icon=abc"))

        assertRejected(result, ukMessages.group.iconInvalid)
    }

    @Test
    fun `createGroup should reject a mark that is not a single emoji`() = runTest {
        val result = groupController.createGroup(chatId, userId, listOf("devs", "\$mark=abc"))

        assertRejected(result, ukMessages.group.markInvalid)
    }

    /** A name starting with `$` would be re-read as a parameter by every command that follows. */
    @Test
    fun `createGroup should reject a name that starts with the parameter prefix`() = runTest {
        val result = groupController.createGroup(chatId, userId, listOf("\$icon=🔥"))

        assertRejected(result, ukMessages.group.usageNew)
    }

    // --- deleteGroup tests ---

    @Test
    fun `deleteGroup should return success when deleted`() = runTest {
        val groupWithMembers = GroupWithMembers(1L, chatId, "devs", "devs", emptyList())
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.success(groupWithMembers)
        coEvery { groupService.deleteGroup(chatId, "devs") } returns ResultContainer.success(Unit)

        val result = groupController.deleteGroup(chatId, userId, listOf("devs"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("devs"))
        assertTrue(result.message.contains("видалена"))
    }

    @Test
    fun `deleteGroup should return AccessDenied when caller is regular member`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns false

        val result = groupController.deleteGroup(chatId, userId, listOf("devs"))

        assertTrue(result is CommandResponse.AccessDenied)
    }

    @Test
    fun `deleteGroup should return error when no args`() = runTest {
        val result = groupController.deleteGroup(chatId, userId, emptyList())

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains("/delgroup"))
    }

    @Test
    fun `deleteGroup should return NotFound when group does not exist`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.failure(
            ResourceNotFoundException("Group", "devs"),
        )

        val result = groupController.deleteGroup(chatId, userId, listOf("devs"))

        assertTrue(result is CommandResponse.NotFound)
        assertTrue(result.identifier == "devs")
    }

    // --- addUserToGroup tests ---

    @Test
    fun `addUserToGroup should return success when user added`() = runTest {
        val groupWithMembers = GroupWithMembers(1L, chatId, "devs", "devs", listOf("alice"))
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.success(groupWithMembers)
        coEvery { groupService.addMemberToGroup(chatId, "devs", "bob") } returns ResultContainer.success(Unit)

        val result = groupController.addUserToGroup(chatId, userId, listOf("devs", "@bob"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("bob"))
        assertTrue(result.message.contains("додано"))
        assertTrue(result.message.contains("devs"))
    }

    @Test
    fun `addUserToGroup should return AccessDenied when caller is regular member`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns false

        val result = groupController.addUserToGroup(chatId, userId, listOf("devs", "@bob"))

        assertTrue(result is CommandResponse.AccessDenied)
    }

    @Test
    fun `addUserToGroup should return error when insufficient args`() = runTest {
        val result = groupController.addUserToGroup(chatId, userId, listOf("devs"))

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains("/addtogroup"))
    }

    @Test
    fun `addUserToGroup should report not registered user in success message`() = runTest {
        val groupWithMembers = GroupWithMembers(1L, chatId, "devs", "devs", emptyList())
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.success(groupWithMembers)
        coEvery { groupService.addMemberToGroup(chatId, "devs", "bob") } returns ResultContainer.failure(
            ValidationException("Invalid user"),
        )

        val result = groupController.addUserToGroup(chatId, userId, listOf("devs", "@bob"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Не додано"))
        assertTrue(result.message.contains("не зареєстровано"))
    }

    @Test
    fun `addUserToGroup should return NotFound when group does not exist`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.failure(
            ResourceNotFoundException("Group", "devs"),
        )

        val result = groupController.addUserToGroup(chatId, userId, listOf("devs", "@bob"))

        assertTrue(result is CommandResponse.NotFound)
        assertTrue(result.resource == "Група")
    }

    @Test
    fun `addUserToGroup should report already in group user in success message`() = runTest {
        val groupWithMembers = GroupWithMembers(1L, chatId, "devs", "devs", listOf("bob"))
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.success(groupWithMembers)
        coEvery { groupService.addMemberToGroup(chatId, "devs", "bob") } returns ResultContainer.failure(
            DuplicateResourceException("Member", "bob"),
        )

        val result = groupController.addUserToGroup(chatId, userId, listOf("devs", "@bob"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("вже в групі"))
    }

    @Test
    fun `addUserToGroup should handle multiple users with partial success`() = runTest {
        val groupWithMembers = GroupWithMembers(1L, chatId, "devs", "devs", emptyList())
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.success(groupWithMembers)
        coEvery { groupService.addMemberToGroup(chatId, "devs", "alice") } returns ResultContainer.success(Unit)
        coEvery { groupService.addMemberToGroup(chatId, "devs", "bob") } returns ResultContainer.failure(
            ValidationException("Not registered"),
        )

        val result = groupController.addUserToGroup(chatId, userId, listOf("devs", "@alice", "@bob"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("@alice"))
        assertTrue(result.message.contains("додано"))
        assertTrue(result.message.contains("Не додано"))
        assertTrue(result.message.contains("@bob"))
    }

    // --- removeUserFromGroup tests ---

    @Test
    fun `removeUserFromGroup should return success when user removed`() = runTest {
        val groupWithMembers = GroupWithMembers(1L, chatId, "devs", "devs", listOf("bob"))
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.success(groupWithMembers)
        coEvery { groupService.removeMemberFromGroup(chatId, "devs", "bob") } returns ResultContainer.success(Unit)

        val result = groupController.removeUserFromGroup(chatId, userId, listOf("devs", "@bob"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("bob"))
        assertTrue(result.message.contains("видалено"))
    }

    @Test
    fun `removeUserFromGroup should return AccessDenied when caller is regular member`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns false

        val result = groupController.removeUserFromGroup(chatId, userId, listOf("devs", "@bob"))

        assertTrue(result is CommandResponse.AccessDenied)
    }

    @Test
    fun `removeUserFromGroup should return error when insufficient args`() = runTest {
        val result = groupController.removeUserFromGroup(chatId, userId, listOf("devs"))

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains("/removefromgroup"))
    }

    @Test
    fun `removeUserFromGroup should return NotFound when group does not exist`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.failure(
            ResourceNotFoundException("Group", "devs"),
        )

        val result = groupController.removeUserFromGroup(chatId, userId, listOf("devs", "@bob"))

        assertTrue(result is CommandResponse.NotFound)
    }

    @Test
    fun `removeUserFromGroup should report not in group user in success message`() = runTest {
        val groupWithMembers = GroupWithMembers(1L, chatId, "devs", "devs", emptyList())
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.success(groupWithMembers)
        coEvery { groupService.removeMemberFromGroup(chatId, "devs", "bob") } returns ResultContainer.failure(
            BusinessException("Member not in group"),
        )

        val result = groupController.removeUserFromGroup(chatId, userId, listOf("devs", "@bob"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Не знайдено в групі"))
        assertTrue(result.message.contains("@bob"))
    }

    @Test
    fun `removeUserFromGroup should handle multiple users with partial success`() = runTest {
        val groupWithMembers = GroupWithMembers(1L, chatId, "devs", "devs", listOf("alice"))
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.success(groupWithMembers)
        coEvery { groupService.removeMemberFromGroup(chatId, "devs", "alice") } returns ResultContainer.success(Unit)
        coEvery { groupService.removeMemberFromGroup(chatId, "devs", "bob") } returns ResultContainer.failure(
            BusinessException("Member not in group"),
        )

        val result = groupController.removeUserFromGroup(chatId, userId, listOf("devs", "@alice", "@bob"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("@alice"))
        assertTrue(result.message.contains("видалено"))
        assertTrue(result.message.contains("Не знайдено в групі"))
        assertTrue(result.message.contains("@bob"))
    }

    // --- grantRole tests ---

    @Test
    fun `grantRole should return success when admin grants moderator role`() = runTest {
        val targetMember = Member(2L, 789L, "bob", "Bob")
        val targetMemberChat = MemberChat(2L, chatId, MemberRole.MEMBER, null)
        coEvery { memberService.getMemberByUsername("bob") } returns ResultContainer.success(targetMember)
        coEvery { memberService.setMemberRole(chatId, 789L, MemberRole.MODERATOR) } returns ResultContainer.success(
            targetMemberChat.copy(role = MemberRole.MODERATOR),
        )

        val result = groupController.grantRole(chatId, userId, listOf("@bob", "moderator"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("bob"))
        assertTrue(result.message.contains("moderator"))
    }

    @Test
    fun `grantRole should return AccessDenied when caller is not admin`() = runTest {
        coEvery { memberService.hasAdminAccess(chatId, userId) } returns false

        val result = groupController.grantRole(chatId, userId, listOf("@bob", "moderator"))

        assertTrue(result is CommandResponse.AccessDenied)
        coVerify(exactly = 0) { memberService.setMemberRole(any(), any(), any()) }
    }

    @Test
    fun `grantRole should report not found user in success message`() = runTest {
        coEvery { memberService.getMemberByUsername("bob") } returns ResultContainer.failure(
            ResourceNotFoundException("Member", "bob"),
        )

        val result = groupController.grantRole(chatId, userId, listOf("@bob", "moderator"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Не знайдено"))
        assertTrue(result.message.contains("@bob"))
        coVerify(exactly = 0) { memberService.setMemberRole(any(), any(), any()) }
    }

    @Test
    fun `grantRole should return error for invalid role name`() = runTest {
        val result = groupController.grantRole(chatId, userId, listOf("@bob", "superadmin"))

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains("Невідома роль"))
        coVerify(exactly = 0) { memberService.setMemberRole(any(), any(), any()) }
    }

    @Test
    fun `grantRole should return error when insufficient args`() = runTest {
        val result = groupController.grantRole(chatId, userId, listOf("@bob"))

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains("/grantrole"))
    }

    @Test
    fun `grantRole should grant role to multiple users`() = runTest {
        val alice = Member(2L, 101L, "alice", "Alice")
        val bob = Member(3L, 102L, "bob", "Bob")
        val aliceChat = MemberChat(2L, chatId, MemberRole.MEMBER, null)
        val bobChat = MemberChat(3L, chatId, MemberRole.MEMBER, null)
        coEvery { memberService.getMemberByUsername("alice") } returns ResultContainer.success(alice)
        coEvery { memberService.getMemberByUsername("bob") } returns ResultContainer.success(bob)
        coEvery { memberService.setMemberRole(chatId, 101L, MemberRole.MODERATOR) } returns
            ResultContainer.success(aliceChat.copy(role = MemberRole.MODERATOR))
        coEvery { memberService.setMemberRole(chatId, 102L, MemberRole.MODERATOR) } returns
            ResultContainer.success(bobChat.copy(role = MemberRole.MODERATOR))

        val result = groupController.grantRole(chatId, userId, listOf("@alice,@bob", "moderator"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("@alice"))
        assertTrue(result.message.contains("@bob"))
        assertTrue(result.message.contains("moderator"))
        coVerify(exactly = 1) { memberService.setMemberRole(chatId, 101L, MemberRole.MODERATOR) }
        coVerify(exactly = 1) { memberService.setMemberRole(chatId, 102L, MemberRole.MODERATOR) }
    }

    @Test
    fun `grantRole should report partial failure when some users not found`() = runTest {
        val alice = Member(2L, 101L, "alice", "Alice")
        val aliceChat = MemberChat(2L, chatId, MemberRole.MEMBER, null)
        coEvery { memberService.getMemberByUsername("alice") } returns ResultContainer.success(alice)
        coEvery { memberService.setMemberRole(chatId, 101L, MemberRole.ADMIN) } returns
            ResultContainer.success(aliceChat.copy(role = MemberRole.ADMIN))
        coEvery { memberService.getMemberByUsername("unknown") } returns ResultContainer.failure(
            ResourceNotFoundException("Member", "unknown"),
        )

        val result = groupController.grantRole(chatId, userId, listOf("@alice,@unknown", "admin"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("@alice"))
        assertTrue(result.message.contains("Не знайдено"))
        assertTrue(result.message.contains("@unknown"))
    }
}
