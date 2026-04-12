package commands

import com.ua.astrumon.domain.model.MemberRole
import infrastructure.BaseE2ETest
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupCommandE2ETest : BaseE2ETest() {

    @BeforeTest
    fun setUpAdmin() {
        // Pre-register the helper bot as ADMIN so moderator-gated commands pass
        registerMember(userId = helperBotId, username = "helper_bot", firstName = "HelperBot", role = MemberRole.ADMIN)
    }

    @Test
    fun `groups command completes without exception when no groups exist`() {
        dispatch("/groups")
        assertTrue(allGroups().isEmpty(), "No groups should be in repo")
    }

    @Test
    fun `newgroup command creates the group in the repository`() {
        dispatch("/newgroup e2egroup")
        assertTrue(allGroups().any { it.name == "e2egroup" }, "Expected 'e2egroup' to be created")
    }

    @Test
    fun `newgroup command duplicate leaves exactly one group`() {
        runBlocking { groupService.createGroup(testChatId, "dupgroup").getOrThrow() }
        dispatch("/newgroup dupgroup")
        val count = allGroups().count { it.name == "dupgroup" }
        assertTrue(count == 1, "Expected exactly one 'dupgroup', got $count")
    }

    @Test
    fun `groups command lists a pre-created group`() {
        runBlocking { groupService.createGroup(testChatId, "listedgroup").getOrThrow() }
        dispatch("/groups")
        assertTrue(allGroups().any { it.name == "listedgroup" }, "Expected 'listedgroup' to be in repo")
    }

    @Test
    fun `addtogroup command adds member to group in repository`() {
        runBlocking {
            groupService.createGroup(testChatId, "addgroup").getOrThrow()
            memberService.createMember(testChatId, 997L, "groupmember", "GroupMember", MemberRole.MEMBER)
        }
        dispatch("/addtogroup addgroup @groupmember")
        val group = allGroups().find { it.name == "addgroup" }
        assertTrue(group?.members?.contains("groupmember") == true, "Expected 'groupmember' in group")
    }

    @Test
    fun `removefromgroup command removes member from group in repository`() {
        runBlocking {
            groupService.createGroup(testChatId, "removegroup").getOrThrow()
            memberService.createMember(testChatId, 996L, "removemember", "RemoveMember", MemberRole.MEMBER)
            groupService.addMemberToGroup(testChatId, "removegroup", "removemember").getOrThrow()
        }
        dispatch("/removefromgroup removegroup @removemember")
        val group = allGroups().find { it.name == "removegroup" }
        assertFalse(group?.members?.contains("removemember") == true, "Expected 'removemember' to be removed from group")
    }

    @Test
    fun `delgroup command removes group from repository`() {
        runBlocking { groupService.createGroup(testChatId, "todeletegroup").getOrThrow() }
        dispatch("/delgroup todeletegroup")
        assertFalse(allGroups().any { it.name == "todeletegroup" }, "Expected 'todeletegroup' to be deleted")
    }
}
