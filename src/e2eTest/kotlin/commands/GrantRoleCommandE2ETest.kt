package commands

import com.ua.astrumon.domain.model.MemberRole
import infrastructure.BaseE2ETest
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GrantRoleCommandE2ETest : BaseE2ETest() {

    @BeforeTest
    fun setUpAdminAndTarget() {
        registerMember(userId = helperBotId, username = "helper_bot", firstName = "HelperBot", role = MemberRole.ADMIN)
        registerMember(userId = 995L, username = "roletarget", firstName = "RoleTarget", role = MemberRole.MEMBER)
    }

    @Test
    fun `grantrole command grants moderator role to target user`() {
        dispatch("/grantrole @roletarget moderator")
        val updated = runBlocking { memberService.getMemberByChatAndUserId(testChatId, 995L).getOrThrow() }
        assertEquals(MemberRole.MODERATOR, updated.role, "Expected roletarget to have MODERATOR role")
    }

    @Test
    fun `grantrole command grants admin role to target user`() {
        dispatch("/grantrole @roletarget admin")
        val updated = runBlocking { memberService.getMemberByChatAndUserId(testChatId, 995L).getOrThrow() }
        assertEquals(MemberRole.ADMIN, updated.role, "Expected roletarget to have ADMIN role")
    }

    @Test
    fun `grantrole command is rejected when caller is not admin`() {
        runBlocking { memberService.setMemberRole(testChatId, helperBotId, MemberRole.MEMBER) }
        dispatch("/grantrole @roletarget moderator")
        // Role should remain MEMBER — the command must have been rejected
        val target = runBlocking { memberService.getMemberByChatAndUserId(testChatId, 995L).getOrThrow() }
        assertEquals(MemberRole.MEMBER, target.role, "Role should not be changed when caller lacks admin access")
    }

    @Test
    fun `grantrole command for nonexistent user leaves repo unchanged`() {
        dispatch("/grantrole @nonexistentuser999 moderator")
        // Only the two pre-registered members should exist
        assertEquals(2, allMembers().size, "No extra member should be created for unknown user")
    }
}
