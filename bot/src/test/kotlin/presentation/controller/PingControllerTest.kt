package presentation.controller

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.model.MemberWithChat
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.domain.bot.service.ChatService
import com.ua.astrumon.domain.bot.service.GroupService
import com.ua.astrumon.domain.bot.service.GroupWithMembers
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.controller.PickerListing
import com.ua.astrumon.presentation.controller.PickerOption
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.controller.PingOutcome
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PingControllerTest {
    private val memberService: MemberService = mockk()
    private val groupService: GroupService = mockk()
    private val chatService: ChatService = mockk()
    private val autoRegisterService: AutoRegisterService = mockk()
    private lateinit var pingController: PingController

    private val chatId = 123L
    private val userId = 456L
    private val member = Member(1L, userId, "alice", "Alice")
    private val bob = Member(2L, 789L, "bob", "Bob")
    private val memberWithChat = MemberWithChat(1L, userId, "alice", "Alice", MemberRole.MEMBER, null)

    @BeforeTest
    fun setup() {
        clearAllMocks()
        pingController = PingController(memberService, groupService, chatService, autoRegisterService)
        coEvery { autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), any()) } returns
            ResultContainer.success(memberWithChat)
        // Default to the plain ping so the pre-readiness assertions stay about ping content.
        coEvery { chatService.isReadinessEnabled(chatId) } returns ResultContainer.success(false)
        // Default: nobody in a group resolves. Tests that expect targets call resolve() themselves.
        coEvery { memberService.getMembersByUsernames(any()) } returns ResultContainer.success(emptyList())
    }

    /**
     * Group membership is resolved in one batch call, and the service already drops usernames with no
     * member row — so "member missing from the database" is expressed by leaving them out here.
     */
    private fun resolve(vararg members: Member) {
        coEvery { memberService.getMembersByUsernames(any()) } returns ResultContainer.success(members.toList())
    }

    /** Unwraps the plain branch — fails loudly if the controller unexpectedly opened a readiness poll. */
    private fun PingOutcome.plainResponse(): CommandResponse {
        assertTrue(this is PingOutcome.Plain, "expected a plain ping, got $this")
        return response
    }

    private fun group(
        id: Long,
        name: String,
        members: List<String>,
        readinessEnabled: Boolean = false,
    ) = GroupWithMembers(id, chatId, name, name, members, readinessEnabled)

    // --- pingAll ---

    @Test
    fun `pingAll should return Success with mentions for all members`() = runTest {
        val members = listOf(
            MemberWithChat(1L, 456L, "alice", "Alice", MemberRole.MEMBER, null),
            MemberWithChat(2L, 789L, "bob", "Bob", MemberRole.MEMBER, null),
        )
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(members)

        val result = pingController.pingAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER, emptyList()).plainResponse()

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("@alice"))
        assertTrue(result.message.contains("@bob"))
    }

    @Test
    fun `pingAll should include extra text in header`() = runTest {
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(
            listOf(MemberWithChat(1L, 456L, "alice", "Alice", MemberRole.MEMBER, null)),
        )

        val result = pingController
            .pingAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("standup", "time"))
            .plainResponse()

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("standup time"))
    }

    @Test
    fun `pingAll should return Success with empty message when no members`() = runTest {
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(emptyList())

        val result = pingController.pingAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER, emptyList()).plainResponse()

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Немає зареєстрованих учасників"))
    }

    @Test
    fun `pingAll should return Error on service failure`() = runTest {
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.failure(DatabaseException("error"))

        val result = pingController.pingAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER, emptyList()).plainResponse()

        assertTrue(result is CommandResponse.Error)
    }

    @Test
    fun `pingAll should return Readiness when the chat has readiness enabled`() = runTest {
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(listOf(memberWithChat))
        coEvery { chatService.isReadinessEnabled(chatId) } returns ResultContainer.success(true)

        val outcome = pingController.pingAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER, emptyList())

        assertTrue(outcome is PingOutcome.Readiness)
        assertEquals(listOf(member), outcome.members)
    }

    @Test
    fun `pingAll should default to Readiness when the chat flag cannot be read`() = runTest {
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(listOf(memberWithChat))
        coEvery { chatService.isReadinessEnabled(chatId) } returns ResultContainer.failure(DatabaseException("db error"))

        val outcome = pingController.pingAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER, emptyList())

        assertTrue(outcome is PingOutcome.Readiness)
    }

    // --- pingGroupById ---

    @Test
    fun `pingGroupById should ping correct group when multiple groups exist`() = runTest {
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(
            listOf(group(1L, "devs", listOf("alice")), group(2L, "qa", listOf("bob"))),
        )
        resolve(member)

        val result = pingController.pingGroupById(chatId, userId, "alice", "Alice", MemberRole.MEMBER, groupId = 1L).plainResponse()

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("@alice"))
        assertTrue(!result.message.contains("@bob"))
    }

    @Test
    fun `pingGroupById should return NotFound when group id does not exist`() = runTest {
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(
            listOf(group(1L, "devs", listOf("alice"))),
        )

        val result = pingController.pingGroupById(chatId, userId, "alice", "Alice", MemberRole.MEMBER, groupId = 999L).plainResponse()

        assertTrue(result is CommandResponse.NotFound)
    }

    @Test
    fun `pingGroupById should return noTargets when group has no registered members`() = runTest {
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(
            listOf(group(1L, "devs", listOf("ghost"))),
        )

        val result = pingController.pingGroupById(chatId, userId, "alice", "Alice", MemberRole.MEMBER, groupId = 1L).plainResponse()

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Немає кого пінгувати"))
    }

    @Test
    fun `pingGroupById should return Error on service failure`() = runTest {
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.failure(DatabaseException("db error"))

        val result = pingController.pingGroupById(chatId, userId, "alice", "Alice", MemberRole.MEMBER, groupId = 1L).plainResponse()

        assertTrue(result is CommandResponse.Error)
    }

    @Test
    fun `pingGroupById message contains group name in header`() = runTest {
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(
            listOf(group(1L, "backend", listOf("alice"))),
        )
        resolve(member)

        val result = pingController.pingGroupById(chatId, userId, "alice", "Alice", MemberRole.MEMBER, groupId = 1L).plainResponse()

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("backend"))
    }

    // --- pingGroup ---

    @Test
    fun `pingGroup should return Success with mentions for group members`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns
            ResultContainer.success(group(1L, "devs", listOf("alice", "bob")))
        resolve(member, bob)

        val result = pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("devs")).plainResponse()

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("@alice"))
        assertTrue(result.message.contains("@bob"))
    }

    @Test
    fun `pingGroup should include extra text in header`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.success(group(1L, "devs", listOf("alice")))
        resolve(member)

        val result = pingController
            .pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("devs", "review", "please"))
            .plainResponse()

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("review please"))
    }

    @Test
    fun `pingGroup should return Error when no args`() = runTest {
        val result = pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, emptyList()).plainResponse()

        assertTrue(result is CommandResponse.Error)
        assertTrue(result.message.contains("/ping"))
    }

    @Test
    fun `pingGroup should return NotFound with available groups when group does not exist`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "unknown") } returns ResultContainer.failure(
            ResourceNotFoundException("Group", "unknown"),
        )
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(
            listOf(group(1L, "devs", emptyList())),
        )

        val result = pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("unknown")).plainResponse()

        assertTrue(result is CommandResponse.NotFound)
        assertTrue(result.identifier == "unknown")
        assertTrue(result.available.contains("devs"))
    }

    @Test
    fun `pingGroup should skip members not found in database`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns
            ResultContainer.success(group(1L, "devs", listOf("alice", "ghost")))
        resolve(member)

        val result = pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("devs")).plainResponse()

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("@alice"))
        assertTrue(!result.message.contains("@ghost"))
    }

    @Test
    fun `pingGroup should return Success with no one to ping when all members invalid`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.success(group(1L, "devs", listOf("ghost")))

        val result = pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("devs")).plainResponse()

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Немає кого пінгувати"))
    }

    @Test
    fun `pingGroup should return Success with no one to ping when group has no members`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns ResultContainer.success(group(1L, "devs", emptyList()))

        val result = pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("devs")).plainResponse()

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Немає кого пінгувати"))
    }

    @Test
    fun `pingGroup should return Readiness when the group has readiness enabled`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns
            ResultContainer.success(group(1L, "devs", listOf("alice"), readinessEnabled = true))
        resolve(member)

        val outcome = pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("devs"))

        assertTrue(outcome is PingOutcome.Readiness)
        assertEquals(listOf(member), outcome.members)
        assertTrue(outcome.header.contains("devs"))
    }

    @Test
    fun `pingGroup should ignore the chat flag and follow the group flag`() = runTest {
        coEvery { chatService.isReadinessEnabled(chatId) } returns ResultContainer.success(true)
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns
            ResultContainer.success(group(1L, "devs", listOf("alice"), readinessEnabled = false))
        resolve(member)

        val outcome = pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("devs"))

        assertTrue(outcome is PingOutcome.Plain)
        coVerify(exactly = 0) { chatService.isReadinessEnabled(any()) }
    }

    @Test
    fun `pingGroup should resolve the whole roster in one batch call`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns
            ResultContainer.success(group(1L, "devs", listOf("alice", "bob")))
        resolve(member, bob)

        pingController.pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("devs"))

        coVerify(exactly = 1) { memberService.getMembersByUsernames(listOf("alice", "bob")) }
        coVerify(exactly = 0) { memberService.getMemberByUsername(any()) }
    }

    @Test
    fun `pingGroup should escape the group name and the caller text in the header`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "de<vs") } returns
            ResultContainer.success(group(1L, "de<vs", listOf("alice")))
        resolve(member)

        val result = pingController
            .pingGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("de<vs", "<b>зараз</b>"))
            .plainResponse()

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("de&lt;vs"), "group name must be escaped")
        assertTrue(result.message.contains("&lt;b&gt;зараз&lt;/b&gt;"), "caller text must be escaped")
        assertTrue(!result.message.contains("<b>"), "no raw markup may reach ParseMode.HTML")
    }

    @Test
    fun `pingAll should escape the caller text in the header`() = runTest {
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(listOf(memberWithChat))

        val result = pingController
            .pingAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER, listOf("<i>усі</i>"))
            .plainResponse()

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("&lt;i&gt;усі&lt;/i&gt;"))
    }

    // --- readiness toggles ---

    @Test
    fun `setGroupReadiness should deny a non-moderator`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns false

        val result = pingController.setGroupReadiness(chatId, userId, "devs", enabled = false)

        assertTrue(result is CommandResponse.AccessDenied)
        coVerify(exactly = 0) { groupService.setReadinessEnabled(any(), any(), any()) }
    }

    @Test
    fun `setGroupReadiness should lowercase the key and report success`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns true
        coEvery { groupService.setReadinessEnabled(chatId, "devs", false) } returns ResultContainer.success(Unit)

        val result = pingController.setGroupReadiness(chatId, userId, "DEVS", enabled = false)

        assertTrue(result is CommandResponse.Success)
        coVerify(exactly = 1) { groupService.setReadinessEnabled(chatId, "devs", false) }
    }

    @Test
    fun `setGroupReadiness should return NotFound with available groups for an unknown group`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns true
        coEvery { groupService.setReadinessEnabled(chatId, "ghost", true) } returns ResultContainer.failure(
            ResourceNotFoundException("Group", "ghost"),
        )
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(
            listOf(group(1L, "devs", emptyList())),
        )

        val result = pingController.setGroupReadiness(chatId, userId, "ghost", enabled = true)

        assertTrue(result is CommandResponse.NotFound)
        assertTrue(result.available.contains("devs"))
    }

    @Test
    fun `setGroupReadiness should return Error on a database failure`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns true
        coEvery { groupService.setReadinessEnabled(chatId, "devs", true) } returns ResultContainer.failure(
            DatabaseException("db error"),
        )

        val result = pingController.setGroupReadiness(chatId, userId, "devs", enabled = true)

        assertTrue(result is CommandResponse.Error)
    }

    @Test
    fun `setChatReadiness should deny a non-moderator`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns false

        val result = pingController.setChatReadiness(chatId, userId, enabled = false)

        assertTrue(result is CommandResponse.AccessDenied)
        coVerify(exactly = 0) { chatService.setReadinessEnabled(any(), any()) }
    }

    @Test
    fun `setChatReadiness should report success for a moderator`() = runTest {
        coEvery { memberService.hasModeratorAccess(chatId, userId) } returns true
        coEvery { chatService.setReadinessEnabled(chatId, false) } returns ResultContainer.success(Unit)

        val result = pingController.setChatReadiness(chatId, userId, enabled = false)

        assertTrue(result is CommandResponse.Success)
        coVerify(exactly = 1) { chatService.setReadinessEnabled(chatId, false) }
    }

    // --- groupsForPicker ---

    @Test
    fun `groupsForPicker should put the all-members option first`() = runTest {
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(
            listOf(group(1L, "devs", listOf("alice")), group(2L, "qa", listOf("bob"))),
        )

        val listing = pingController.groupsForPicker(chatId, userId, "alice", "Alice", MemberRole.MEMBER)

        assertTrue(listing is PickerListing.Show)
        assertEquals(
            listOf(
                PickerOption(PingController.ALL_MEMBERS_ID, BotMessages.Ping.allMembersOption),
                PickerOption(1L, "devs"),
                PickerOption(2L, "qa"),
            ),
            listing.options,
        )
    }

    @Test
    fun `groupsForPicker should return the all-members option alone when the chat has no groups`() = runTest {
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(emptyList())

        val listing = pingController.groupsForPicker(chatId, userId, "alice", "Alice", MemberRole.MEMBER)

        assertTrue(listing is PickerListing.Show)
        assertEquals(listOf(PickerOption(PingController.ALL_MEMBERS_ID, BotMessages.Ping.allMembersOption)), listing.options)
    }

    @Test
    fun `groupsForPicker should return Reject when loading groups fails`() = runTest {
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.failure(DatabaseException("db error"))

        val listing = pingController.groupsForPicker(chatId, userId, "alice", "Alice", MemberRole.MEMBER)

        assertTrue(listing is PickerListing.Reject)
        assertTrue(listing.response is CommandResponse.Error)
    }
}
