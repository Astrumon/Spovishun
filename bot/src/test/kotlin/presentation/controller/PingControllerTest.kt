package presentation.controller

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.model.MemberWithChat
import com.ua.astrumon.domain.service.AutoRegisterService
import com.ua.astrumon.domain.service.GroupService
import com.ua.astrumon.domain.service.GroupWithMembers
import com.ua.astrumon.domain.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.controller.PingController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PingControllerTest {
    private val memberService: MemberService = mockk()
    private val groupService: GroupService = mockk()
    private val autoRegisterService: AutoRegisterService = mockk()
    private lateinit var pingController: PingController

    private val chatId = 123L
    private val userId = 456L
    private val member = Member(1L, userId, "alice", "Alice")
    private val memberWithChat = MemberWithChat(1L, userId, "alice", "Alice", MemberRole.MEMBER, null)

    @BeforeTest
    fun setup() {
        clearAllMocks()
        pingController = PingController(memberService, groupService, autoRegisterService)
        coEvery { autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), any()) } returns
            ResultContainer.success(memberWithChat)
    }

    // --- pingAll ---

    @Test
    fun `pingAll should return Success with mentions for all members`() = runTest {
        val members = listOf(
            MemberWithChat(1L, 456L, "alice", "Alice", MemberRole.MEMBER, null),
            MemberWithChat(2L, 789L, "bob", "Bob", MemberRole.MEMBER, null),
        )
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(members)

        val result = pingController.pingAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER, emptyList())

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("@alice"))
        assertTrue(result.message.contains("@bob"))
    }

    @Test
    fun `pingAll should include extra text in header`() = runTest {
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(
            listOf(MemberWithChat(1L, 456L, "alice", "Alice", MemberRole.MEMBER, null)),
        )

        val result = pingController.pingAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("standup", "time"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("standup time"))
    }

    @Test
    fun `pingAll should return Success with empty message when no members`() = runTest {
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(emptyList())

        val result = pingController.pingAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER, emptyList())

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Немає зареєстрованих учасників"))
    }

    @Test
    fun `pingAll should return Error on service failure`() = runTest {
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.failure(DatabaseException("error"))

        val result = pingController.pingAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER, emptyList())

        assertTrue(result is CommandResponse.Error)
    }

    // --- pingGroupById ---

    @Test
    fun `pingGroupById should ping correct group when multiple groups exist`() = runTest {
        val targetGroup = GroupWithMembers(1L, chatId, "devs", "devs", listOf("alice"))
        val otherGroup = GroupWithMembers(2L, chatId, "qa", "qa", listOf("bob"))
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(listOf(targetGroup, otherGroup))
        coEvery { memberService.getMemberByUsername("alice") } returns ResultContainer.success(member)

        val result = pingController.pingGroupById(chatId, userId, "alice", "Alice", MemberRole.MEMBER, groupId = 1L)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("@alice"))
        assertTrue(!result.message.contains("@bob"))
    }

    @Test
    fun `pingGroupById should return NotFound when group id does not exist`() = runTest {
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(
            listOf(GroupWithMembers(1L, chatId, "devs", "devs", listOf("alice"))),
        )

        val result = pingController.pingGroupById(chatId, userId, "alice", "Alice", MemberRole.MEMBER, groupId = 999L)

        assertTrue(result is CommandResponse.NotFound)
    }

    @Test
    fun `pingGroupById should return noTargets when group has no registered members`() = runTest {
        val group = GroupWithMembers(1L, chatId, "devs", "devs", listOf("ghost"))
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(listOf(group))
        coEvery { memberService.getMemberByUsername("ghost") } returns ResultContainer.failure(
            ResourceNotFoundException("Member", "ghost"),
        )

        val result = pingController.pingGroupById(chatId, userId, "alice", "Alice", MemberRole.MEMBER, groupId = 1L)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Немає кого пінгувати"))
    }

    @Test
    fun `pingGroupById should return Error on service failure`() = runTest {
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.failure(DatabaseException("db error"))

        val result = pingController.pingGroupById(chatId, userId, "alice", "Alice", MemberRole.MEMBER, groupId = 1L)

        assertTrue(result is CommandResponse.Error)
    }

    @Test
    fun `pingGroupById message contains group name in header`() = runTest {
        val group = GroupWithMembers(1L, chatId, "backend", "backend", listOf("alice"))
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(listOf(group))
        coEvery { memberService.getMemberByUsername("alice") } returns ResultContainer.success(member)

        val result = pingController.pingGroupById(chatId, userId, "alice", "Alice", MemberRole.MEMBER, groupId = 1L)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("backend"))
    }

    // --- pingGroup ---

    @Test
    fun `pingGroup should return Success with mentions for group members`() = runTest {
        val group = GroupWithMembers(1L, chatId, "devs", "devs", listOf("alice", "bob"))
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.success(group)
        coEvery { memberService.getMemberByUsername("alice") } returns ResultContainer.success(member)
        coEvery { memberService.getMemberByUsername("bob") } returns ResultContainer.success(
            Member(2L, 789L, "bob", "Bob"),
        )

        val result = pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("devs"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("@alice"))
        assertTrue(result.message.contains("@bob"))
    }

    @Test
    fun `pingGroup should include extra text in header`() = runTest {
        val group = GroupWithMembers(1L, chatId, "devs", "devs", listOf("alice"))
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.success(group)
        coEvery { memberService.getMemberByUsername("alice") } returns ResultContainer.success(member)

        val result = pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("devs", "review", "please"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("review please"))
    }

    @Test
    fun `pingGroup should return Error when no args`() = runTest {
        val result = pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, emptyList())

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains("/ping"))
    }

    @Test
    fun `pingGroup should return NotFound with available groups when group does not exist`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "unknown") } returns ResultContainer.failure(
            ResourceNotFoundException("Group", "unknown"),
        )
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(
            listOf(GroupWithMembers(1L, chatId, "devs", "devs", emptyList())),
        )

        val result = pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("unknown"))

        assertTrue(result is CommandResponse.NotFound)
        assertTrue(result.identifier == "unknown")
        assertTrue(result.available.contains("devs"))
    }

    @Test
    fun `pingGroup should skip members not found in database`() = runTest {
        val group = GroupWithMembers(1L, chatId, "devs", "devs", listOf("alice", "ghost"))
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.success(group)
        coEvery { memberService.getMemberByUsername("alice") } returns ResultContainer.success(member)
        coEvery { memberService.getMemberByUsername("ghost") } returns ResultContainer.failure(
            ResourceNotFoundException("Member", "ghost"),
        )

        val result = pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("devs"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("@alice"))
        assertTrue(!result.message.contains("@ghost"))
    }

    @Test
    fun `pingGroup should return Success with no one to ping when all members invalid`() = runTest {
        val group = GroupWithMembers(1L, chatId, "devs", "devs", listOf("ghost"))
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.success(group)
        coEvery { memberService.getMemberByUsername("ghost") } returns ResultContainer.failure(
            ResourceNotFoundException("Member", "ghost"),
        )

        val result = pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("devs"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Немає кого пінгувати"))
    }

    @Test
    fun `pingGroup should return Success with no one to ping when group has no members`() = runTest {
        val group = GroupWithMembers(1L, chatId, "devs", "devs", emptyList())
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.success(group)

        val result = pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("devs"))

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Немає кого пінгувати"))
    }
}
