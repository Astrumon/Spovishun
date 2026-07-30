package commands

import com.ua.astrumon.presentation.bot.BotMessages
import infrastructure.BaseE2ETest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Ping targeting and copy are covered in `PingCommandIntegrationTest`. What only Telegram can
 * settle is whether `@name` in the delivered text actually becomes a mention entity — the entire
 * point of the feature.
 */
class PingCommandE2ETest : BaseE2ETest() {
    @Test
    fun `all command delivers mentions for every registered member`() {
        registerMember(userId = 999L, username = "testpinguser", firstName = "PingTest")

        val sent = dispatchExpectingReply("/all")
        val text = sent.text.orEmpty()

        assertTrue(text.startsWith("📢"), "The all-members ping keeps its megaphone header")
        assertTrue(text.contains(BotMessages.Ping.iconAll), "Header must carry one icon per member")
        assertTrue(text.contains("@testpinguser"), "Registered member must be mentioned")
        assertTrue(sent.entities.orEmpty().isNotEmpty(), "Telegram must recognise the mentions as entities")
    }

    @Test
    fun `ping command with known group delivers the group header and mentions`() {
        registerMember(userId = 998L, username = "pingmember", firstName = "PingMember")
        runBlocking {
            groupService.createGroup(testChatId, "testpinggroup").getOrThrow()
            groupService.addMemberToGroup(testChatId, "testpinggroup", "pingmember").getOrThrow()
        }

        val sent = dispatchExpectingReply("/ping testpinggroup")
        val text = sent.text.orEmpty()

        assertTrue(text.contains("testpinggroup"), "Group name must appear in the delivered header")
        assertTrue(text.contains("@pingmember"), "Group member must be mentioned")
        assertTrue(sent.entities.orEmpty().isNotEmpty(), "Telegram must recognise the mentions as entities")
    }
}
