package commands

import infrastructure.BaseE2ETest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Registration behaviour for `/start` is proven in `StartCommandIntegrationTest`. What is left here
 * is the one thing a mocked Bot cannot answer: whether Telegram accepts the welcome card's markup.
 */
class StartCommandE2ETest : BaseE2ETest() {
    /**
     * The welcome card is the project's richest HTML payload — bold blocks plus `&lt;` escapes.
     * Only a real send proves Telegram accepted that markup: the response comes back with the tags
     * consumed into entities and the escapes decoded.
     */
    @Test
    fun `start command delivers the welcome card with HTML parsed by Telegram`() {
        val sent = dispatchExpectingReply("/start")
        val text = sent.text.orEmpty()

        assertTrue(ukMessages.welcome.message().contains("<b>"), "Precondition: the welcome copy is HTML")
        assertFalse(text.contains("<b>"), "Telegram must consume the bold tags rather than echo them literally")
        assertTrue(text.contains("/ping <група>"), "The &lt;…&gt; escapes must arrive decoded")
        assertTrue(sent.entities.orEmpty().isNotEmpty(), "Parsed HTML must produce message entities")
        assertTrue(allMembers().any { it.userId == helperBotId }, "The sender is registered on the way through")
    }
}
