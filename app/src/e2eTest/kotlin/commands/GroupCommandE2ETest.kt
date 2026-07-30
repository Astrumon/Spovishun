package commands

import com.ua.astrumon.domain.bot.model.MemberRole
import infrastructure.BaseE2ETest
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Group CRUD and its role gates are covered end to end over PostgreSQL in
 * `GroupCommandIntegrationTest`. The two cases kept here are the ones whose payload Telegram has an
 * opinion about: the creation confirmation (which embeds a user-supplied name) and the bold-headed
 * listing.
 */
class GroupCommandE2ETest : BaseE2ETest() {
    @BeforeTest
    fun setUpAdmin() {
        // Pre-register the helper bot as ADMIN so moderator-gated commands pass
        registerMember(userId = helperBotId, username = "helper_bot", firstName = "HelperBot", role = MemberRole.ADMIN)
    }

    @Test
    fun `newgroup command creates the group and confirms it in the chat`() {
        val text = dispatchExpectingReply("/newgroup e2egroup").text.orEmpty()

        assertTrue(allGroups().any { it.name == "e2egroup" }, "Expected 'e2egroup' to be created")
        assertTrue(text.contains("e2egroup"), "The confirmation must name the group that was created")
        assertTrue(text.contains("/ping e2egroup"), "The confirmation must show how to call the new group")
    }

    @Test
    fun `groups command delivers the group listing with parsed HTML`() {
        runBlocking { groupService.createGroup(testChatId, "listedgroup").getOrThrow() }

        val sent = dispatchExpectingReply("/groups")
        val text = sent.text.orEmpty()

        assertTrue(text.contains("listedgroup"), "Expected 'listedgroup' in the delivered listing")
        assertFalse(text.contains("<b>"), "Bold tags must be consumed by Telegram")
        assertTrue(sent.entities.orEmpty().isNotEmpty(), "Parsed HTML must produce message entities")
    }
}
