package commands

import com.ua.astrumon.presentation.bot.BotMessages
import infrastructure.BaseE2ETest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `/whatsnew $h` renders the release-notes history — the longest HTML message the bot produces,
 * built from a bundled resource rather than from the DB.
 *
 * Worth an e2e case for one reason: length and markup are exactly where a real send fails while a
 * mocked one cannot. Telegram rejects a malformed tag outright and refuses anything past 4096
 * characters, so a passing round trip is the only proof the payload is deliverable at all. The
 * formatting itself is unit-tested in `:bot` (`ReleaseNotesFormatterTest`) and the reply contents in
 * `WhatsNewCommandIntegrationTest`.
 */
class WhatsNewCommandE2ETest : BaseE2ETest() {
    @Test
    fun `whatsnew history is accepted and parsed by Telegram`() {
        val sent = dispatchExpectingReply("/whatsnew \$h")
        val text = sent.text.orEmpty()

        // Reaching this point already proves the payload fit inside Telegram's 4096-character limit
        // and parsed cleanly — either would have come back as an API error and failed the send.
        assertTrue(text.startsWith(BotMessages.WhatsNew.prefix.trim()), "History keeps the whatsnew prefix")
        assertFalse(text.contains("<b>"), "Bold tags must be consumed by Telegram, not echoed literally")
        assertTrue(sent.entities.orEmpty().isNotEmpty(), "Parsed HTML must produce message entities")
    }
}
