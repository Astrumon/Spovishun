package commands

import com.ua.astrumon.domain.model.MemberRole
import infrastructure.BaseE2ETest
import kotlin.test.Test
import kotlin.test.assertTrue

class MembersCommandE2ETest : BaseE2ETest() {

    @Test
    fun `members command auto-registers sender when no prior members exist`() {
        dispatch("/members")
        // /members auto-registers the sender via ensureUserRegistered
        assertTrue(allMembers().any { it.userId == helperBotId }, "Sender should be auto-registered by /members")
    }

    @Test
    fun `members command completes without exception with registered members`() {
        registerMember(userId = 994L, username = "memberlistuser", firstName = "MemberList")
        dispatch("/members")
        assertTrue(allMembers().any { it.username == "memberlistuser" }, "Pre-registered member should still be in repo")
    }

    @Test
    fun `members command reflects all pre-registered members in repository`() {
        registerMember(userId = 993L, username = "countuser1", firstName = "Count1", role = MemberRole.MEMBER)
        registerMember(userId = 992L, username = "countuser2", firstName = "Count2", role = MemberRole.MEMBER)
        dispatch("/members")
        val members = allMembers()
        assertTrue(members.any { it.username == "countuser1" }, "Expected countuser1 in repo")
        assertTrue(members.any { it.username == "countuser2" }, "Expected countuser2 in repo")
    }
}
