package presentation.controller

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.model.MemberWithChat
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.domain.bot.service.GroupService
import com.ua.astrumon.domain.bot.service.GroupWithMembers
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.controller.PickerListing
import com.ua.astrumon.presentation.controller.PickerOption
import com.ua.astrumon.presentation.controller.RandomController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import presentation.testMessagesProvider
import presentation.ukMessages
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RandomControllerTest {
    private val memberService: MemberService = mockk()
    private val groupService: GroupService = mockk()
    private val autoRegisterService: AutoRegisterService = mockk()
    private lateinit var controller: RandomController

    private val chatId = 123L
    private val userId = 456L
    private val memberWithChat = MemberWithChat(1L, userId, "alice", "Alice", MemberRole.MEMBER, null)

    private fun member(
        id: Long,
        username: String,
    ) = MemberWithChat(id, id, username, username, MemberRole.MEMBER, null)

    private fun group(
        key: String,
        members: List<String>,
    ) = GroupWithMembers(1L, chatId, key, key, members)

    @BeforeTest
    fun setup() {
        clearAllMocks()
        controller = RandomController(memberService, groupService, autoRegisterService, testMessagesProvider())
        coEvery { autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), any()) } returns
            ResultContainer.success(memberWithChat)
    }

    // --- pickRandomAll ---

    @Test
    fun `pickRandomAll should return Success with one of the registered members`() = runTest {
        val members = listOf(member(1L, "alice"), member(2L, "bob"))
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(members)

        val result = controller.pickRandomAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER)

        assertTrue(result is CommandResponse.Success)
        val usernames = members.map { it.username }
        assertTrue(usernames.any { result.message.contains(it) })
    }

    @Test
    fun `pickRandomAll should return Success with the single member when list has one entry`() = runTest {
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(listOf(member(1L, "solo")))

        val result = controller.pickRandomAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("solo"))
    }

    @Test
    fun `pickRandomAll should return Success with no_registered message when list is empty`() = runTest {
        coEvery { memberService.getAllMembersInChat(chatId) } returns ResultContainer.success(emptyList())

        val result = controller.pickRandomAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Немає зареєстрованих учасників"))
    }

    @Test
    fun `pickRandomAll should return Error when memberService fails`() = runTest {
        coEvery { memberService.getAllMembersInChat(chatId) } returns
            ResultContainer.failure(DatabaseException("db error"))

        val result = controller.pickRandomAll(chatId, userId, "alice", "Alice", MemberRole.MEMBER)

        assertTrue(result is CommandResponse.Error)
    }

    // --- pickRandomFromGroup ---

    @Test
    fun `pickRandomFromGroup should return Success with one of the group members`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "devs") } returns
            ResultContainer.success(group("devs", listOf("alice", "bob")))

        val result = controller.pickRandomFromGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, "devs")

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("alice") || result.message.contains("bob"))
    }

    @Test
    fun `pickRandomFromGroup should return Success with the single member when group has one entry`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "solo") } returns
            ResultContainer.success(group("solo", listOf("onlyone")))

        val result = controller.pickRandomFromGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, "solo")

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("onlyone"))
    }

    @Test
    fun `pickRandomFromGroup should return Success with empty_group message when group has no members`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "empty") } returns
            ResultContainer.success(group("empty", emptyList()))

        val result = controller.pickRandomFromGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, "empty")

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("немає учасників"))
    }

    @Test
    fun `pickRandomFromGroup should return NotFound when group does not exist`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "ghost") } returns
            ResultContainer.failure(ResourceNotFoundException("Група", "ghost"))
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns
            ResultContainer.success(listOf(group("devs", emptyList())))

        val result = controller.pickRandomFromGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, "ghost")

        assertTrue(result is CommandResponse.NotFound)
        assertTrue((result as CommandResponse.NotFound).identifier == "ghost")
        assertTrue(result.available.contains("devs"))
    }

    @Test
    fun `pickRandomFromGroup should return NotFound with empty available when getAllGroupsWithMembers fails`() = runTest {
        coEvery { groupService.getGroupByKey(chatId, "ghost") } returns
            ResultContainer.failure(ResourceNotFoundException("Група", "ghost"))
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns
            ResultContainer.failure(DatabaseException("db error"))

        val result = controller.pickRandomFromGroup(chatId, userId, "alice", "Alice", MemberRole.MEMBER, "ghost")

        assertTrue(result is CommandResponse.NotFound)
        assertTrue((result as CommandResponse.NotFound).available.isEmpty())
    }

    // --- groupsForPicker ---

    @Test
    fun `groupsForPicker should list the all-members option first followed by every group`() = runTest {
        val devs = GroupWithMembers(11L, chatId, "devs", "Devs", listOf("alice"))
        val qa = GroupWithMembers(12L, chatId, "qa", "QA", listOf("bob"))
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(listOf(devs, qa))

        val result = controller.groupsForPicker(chatId, userId, "alice", "Alice", MemberRole.MEMBER)

        assertTrue(result is PickerListing.Show)
        assertEquals(
            listOf(
                PickerOption(RandomController.ALL_MEMBERS_ID, ukMessages.random.allMembersOption),
                PickerOption(11L, "Devs"),
                PickerOption(12L, "QA"),
            ),
            result.options,
        )
    }

    @Test
    fun `groupsForPicker should return an empty listing when the chat has no groups`() = runTest {
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.success(emptyList())

        val result = controller.groupsForPicker(chatId, userId, "alice", "Alice", MemberRole.MEMBER)

        assertTrue(result is PickerListing.Show)
        assertTrue(result.options.isEmpty())
    }

    @Test
    fun `groupsForPicker should reject when groupService fails`() = runTest {
        coEvery { groupService.getAllGroupsWithMembers(chatId) } returns ResultContainer.failure(DatabaseException("db error"))

        val result = controller.groupsForPicker(chatId, userId, "alice", "Alice", MemberRole.MEMBER)

        assertTrue(result is PickerListing.Reject)
        assertTrue(result.response is CommandResponse.Error)
    }

    // --- pickRandomFromGroupById ---

    @Test
    fun `pickRandomFromGroupById should return Success with one of the group members`() = runTest {
        val devs = GroupWithMembers(11L, chatId, "devs", "Devs", listOf("alice", "bob"))
        coEvery { groupService.getGroupById(chatId, 11L) } returns ResultContainer.success(devs)

        val result = controller.pickRandomFromGroupById(chatId, userId, "alice", "Alice", MemberRole.MEMBER, 11L)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("alice") || result.message.contains("bob"))
    }

    @Test
    fun `pickRandomFromGroupById should return Success with empty_group message when group has no members`() = runTest {
        val empty = GroupWithMembers(11L, chatId, "empty", "Empty", emptyList())
        coEvery { groupService.getGroupById(chatId, 11L) } returns ResultContainer.success(empty)

        val result = controller.pickRandomFromGroupById(chatId, userId, "alice", "Alice", MemberRole.MEMBER, 11L)

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("немає учасників"))
    }

    @Test
    fun `pickRandomFromGroupById should return NotFound when no group has the given id`() = runTest {
        coEvery { groupService.getGroupById(chatId, 99L) } returns ResultContainer.failure(ResourceNotFoundException("Group", "99"))

        val result = controller.pickRandomFromGroupById(chatId, userId, "alice", "Alice", MemberRole.MEMBER, 99L)

        assertTrue(result is CommandResponse.NotFound)
        assertEquals("99", result.identifier)
    }

    @Test
    fun `pickRandomFromGroupById should return Error when groupService fails`() = runTest {
        coEvery { groupService.getGroupById(chatId, 11L) } returns ResultContainer.failure(DatabaseException("db error"))

        val result = controller.pickRandomFromGroupById(chatId, userId, "alice", "Alice", MemberRole.MEMBER, 11L)

        assertTrue(result is CommandResponse.Error)
    }
}
