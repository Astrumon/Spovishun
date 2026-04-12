package commands

import infrastructure.BaseE2ETest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class PingCommandE2ETest : BaseE2ETest() {

    @Test
    fun `all command auto-registers sender even when no prior members exist`() {
        dispatch("/all")
        // /all auto-registers the sender via ensureUserRegistered
        assertTrue(allMembers().any { it.userId == helperBotId }, "Sender should be auto-registered by /all")
    }

    @Test
    fun `all command completes without exception when members are registered`() {
        registerMember(userId = 999L, username = "testpinguser", firstName = "PingTest")
        dispatch("/all")
        assertTrue(allMembers().any { it.username == "testpinguser" }, "Pre-registered member should still be in repo")
    }

    @Test
    fun `ping command without group name completes without exception`() {
        dispatch("/ping")
        // Read-only command — no state change expected, just must not throw
    }

    @Test
    fun `ping command with unknown group completes without exception`() {
        dispatch("/ping unknowngroup999")
        // Read-only command — no state change expected, just must not throw
    }

    @Test
    fun `ping command with known group completes without exception`() {
        registerMember(userId = 998L, username = "pingmember", firstName = "PingMember")
        runBlocking {
            groupService.createGroup(testChatId, "testpinggroup").getOrThrow()
            groupService.addMemberToGroup(testChatId, "testpinggroup", "pingmember").getOrThrow()
        }
        dispatch("/ping testpinggroup")
        val groups = allGroups()
        assertTrue(groups.any { it.name == "testpinggroup" }, "Group should still exist after /ping")
    }
}
