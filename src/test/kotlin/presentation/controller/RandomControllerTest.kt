package presentation.controller

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.model.MemberWithChat
import com.ua.astrumon.domain.service.AutoRegisterService
import com.ua.astrumon.domain.service.GroupService
import com.ua.astrumon.domain.service.GroupWithMembers
import com.ua.astrumon.domain.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.controller.RandomController
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class RandomControllerTest {

    private val memberService: MemberService = mockk()
    private val groupService: GroupService = mockk()
    private val autoRegisterService: AutoRegisterService = mockk()
    private lateinit var controller: RandomController

    private val chatId = 123L
    private val userId = 456L
    private val memberWithChat = MemberWithChat(1L, userId, "alice", "Alice", MemberRole.MEMBER, null)

    private fun member(id: Long, username: String) =
        MemberWithChat(id, id, username, username, MemberRole.MEMBER, null)

    private fun group(key: String, members: List<String>) =
        GroupWithMembers(1L, chatId, key, key, members)

    @BeforeTest
    fun setup() {
        clearAllMocks()
        controller = RandomController(memberService, groupService, autoRegisterService)
        coEvery { autoRegisterService.ensureUserRegistered(any(), any(), any(), any(), any()) } returns ResultContainer.success(memberWithChat)
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
}
