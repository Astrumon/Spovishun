package commands

import com.ua.astrumon.domain.bot.model.MemberRole
import infrastructure.BaseE2ETest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * List contents and role badges are asserted in `MembersCommandIntegrationTest`. Here the question
 * is only whether the rendered list survives the trip through Telegram's HTML parser.
 */
class MembersCommandE2ETest : BaseE2ETest() {
    @Test
    fun `members list is delivered with its members and parsed HTML header`() {
        registerMember(userId = 993L, username = "countuser1", firstName = "Count1", role = MemberRole.MEMBER)
        registerMember(userId = 992L, username = "countuser2", firstName = "Count2", role = MemberRole.MEMBER)

        val sent = dispatchExpectingReply("/members")
        val text = sent.text.orEmpty()

        assertTrue(text.contains("countuser1"), "Expected countuser1 in the delivered list")
        assertTrue(text.contains("countuser2"), "Expected countuser2 in the delivered list")
        assertFalse(text.contains("<b>"), "Bold tags must be consumed by Telegram")
        assertTrue(sent.entities.orEmpty().isNotEmpty(), "Parsed HTML must produce message entities")
    }
}
