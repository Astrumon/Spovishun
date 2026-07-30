package commands

import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.controller.PickerListing
import com.ua.astrumon.presentation.controller.PingController
import infrastructure.BaseIntegrationTest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Group-picker logic behind the args-less `/ping`: which options the listing offers, and what
 * [PingController.pingGroupById] answers for each kind of group id.
 *
 * These are controller-level assertions over real PostgreSQL — no Telegram behaviour is involved,
 * so they belong here rather than in the e2e suite they were written in (spovishun-160). What e2e
 * still owns is the part only Telegram can confirm: that the rendered keyboard is actually accepted.
 */
class PingGroupSelectionIntegrationTest : BaseIntegrationTest() {
    @Test
    fun `ping picker lists the all-members option first followed by every group`() = runTest {
        registerMember()
        groupService.createGroup(testChatId, "devs").getOrThrow()
        groupService.addMemberToGroup(testChatId, "devs", testUsername).getOrThrow()

        assertPickerOffersAllOptionFirst()
    }

    /**
     * The `All` option is prepended unconditionally, so a chat with zero groups still gets a menu
     * instead of the old "no groups" text — otherwise the default option would be unreachable there.
     */
    @Test
    fun `ping picker still offers the all-members option when no groups exist`() = runTest {
        registerMember()

        assertEquals(0, groupService.getAllGroupsWithMembers(testChatId).getOrThrow().size, "Precondition: no groups")
        assertPickerOffersAllOptionFirst()
    }

    @Test
    fun `pingGroupById puts the group name and member mention in the header`() = runTest {
        registerMember()
        groupService.createGroup(testChatId, "backend").getOrThrow()
        groupService.addMemberToGroup(testChatId, "backend", testUsername).getOrThrow()
        val group = groupService.getAllGroupsWithMembers(testChatId).getOrThrow().first { it.name == "backend" }

        val result = pingController.pingGroupById(
            testChatId,
            testUserId,
            testUsername,
            testFirstName,
            MemberRole.MEMBER,
            group.id,
        )

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("backend"), "Group name should appear in the ping header")
        assertTrue(result.message.contains("@$testUsername"), "Member mention should be in the message")
    }

    @Test
    fun `pingGroupById returns NotFound for non-existent group id`() = runTest {
        val result = pingController.pingGroupById(
            testChatId,
            testUserId,
            testUsername,
            testFirstName,
            MemberRole.MEMBER,
            Long.MAX_VALUE,
        )

        assertTrue(result is CommandResponse.NotFound)
    }

    @Test
    fun `pingGroupById returns noTargets when group has no registered members`() = runTest {
        chatService.ensureChat(testChatId, null, null).getOrThrow()
        groupService.createGroup(testChatId, "empty_squad").getOrThrow()
        val group = groupService.getAllGroupsWithMembers(testChatId).getOrThrow().first { it.name == "empty_squad" }

        val result = pingController.pingGroupById(
            testChatId,
            testUserId,
            testUsername,
            testFirstName,
            MemberRole.MEMBER,
            group.id,
        )

        assertTrue(result is CommandResponse.Success)
        assertTrue(result.message.contains("Немає кого пінгувати"))
    }

    private suspend fun assertPickerOffersAllOptionFirst() {
        val listing = pingController.groupsForPicker(testChatId, testUserId, testUsername, testFirstName, MemberRole.MEMBER)
        val groupCount = groupService.getAllGroupsWithMembers(testChatId).getOrThrow().size

        assertTrue(listing is PickerListing.Show, "Menu must render, not fall back to text")
        assertEquals(PingController.ALL_MEMBERS_ID, listing.options.first().id, "All option must come first")
        assertEquals(groupCount + 1, listing.options.size, "Menu must hold the All option plus every group")
    }
}
