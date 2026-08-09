package presentation.util

import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.presentation.bot.handler.ReadinessSession
import com.ua.astrumon.presentation.bot.handler.ReadinessVote
import com.ua.astrumon.presentation.util.ReadinessRenderer
import presentation.ukMessages
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadinessRendererTest {
    private val alice = Member(1L, 100L, "alice", "Alice")
    private val bob = Member(2L, 200L, "bob", "Bob")
    private val session = ReadinessSession(ukMessages, "📣 devs 🦞", listOf(alice, bob))

    @Test
    fun `should mark everyone pending before the first vote`() {
        val text = ReadinessRenderer.renderActive(session)

        assertTrue(text.startsWith("📣 devs 🦞"))
        assertTrue(text.contains("⏳ @alice"))
        assertTrue(text.contains("⏳ @bob"))
    }

    @Test
    fun `should show each member their own status icon`() {
        val voted = session
            .withVote(alice.userId, ReadinessVote.ACCEPTED)
            .withVote(bob.userId, ReadinessVote.DECLINED)

        val text = ReadinessRenderer.renderActive(voted)

        assertTrue(text.contains("👍 @alice"))
        assertTrue(text.contains("👎 @bob"))
    }

    @Test
    fun `should count accepted declined and unanswered in the final summary`() {
        val voted = session.withVote(alice.userId, ReadinessVote.ACCEPTED)

        val text = ReadinessRenderer.renderFinal(voted)

        assertTrue(text.contains("👍 1"))
        assertTrue(text.contains("👎 0"))
        assertTrue(text.contains("⏳ 1"))
    }

    @Test
    fun `should withhold the summary while the poll is still open`() {
        assertFalse(ReadinessRenderer.renderActive(session).contains("Підсумок"))
        assertTrue(ReadinessRenderer.renderFinal(session).contains("Підсумок"))
    }

    @Test
    fun `should escape a first name when the member has no real username`() {
        val synthetic = Member(3L, 300L, "user_300", "<b>Ha</b>")
        val text = ReadinessRenderer.renderActive(ReadinessSession(ukMessages, "header", listOf(synthetic)))

        assertTrue(text.contains("tg://user?id=300"))
        assertTrue(text.contains("&lt;b&gt;Ha&lt;/b&gt;"))
    }
}
