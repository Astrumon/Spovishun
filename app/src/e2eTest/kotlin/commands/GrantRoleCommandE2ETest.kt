package commands

import com.ua.astrumon.domain.bot.model.MemberRole
import infrastructure.BaseE2ETest
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Role-grant permutations — non-admin callers, unknown users, multiple targets, invalid role
 * strings — are covered in `GrantRoleCommandIntegrationTest`. One case stays here to prove the
 * confirmation, which interpolates a user-supplied username, is actually deliverable.
 */
class GrantRoleCommandE2ETest : BaseE2ETest() {
    @BeforeTest
    fun setUpAdminAndTarget() {
        registerMember(userId = helperBotId, username = "helper_bot", firstName = "HelperBot", role = MemberRole.ADMIN)
        registerMember(userId = 995L, username = "roletarget", firstName = "RoleTarget", role = MemberRole.MEMBER)
    }

    @Test
    fun `grantrole delivers the confirmation and updates the target role`() {
        val text = dispatchExpectingReply("/grantrole @roletarget moderator").text.orEmpty()

        val updated = runBlocking { memberService.getMemberChatByUserId(testChatId, 995L).getOrThrow() }
        assertEquals(MemberRole.MODERATOR, updated.role, "Expected roletarget to have MODERATOR role")
        assertTrue(text.contains("roletarget"), "The confirmation must name the user whose role changed")
    }
}
