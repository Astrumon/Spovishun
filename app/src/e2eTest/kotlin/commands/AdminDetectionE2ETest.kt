package commands

import com.github.kotlintelegrambot.entities.ChatId
import com.ua.astrumon.domain.bot.model.MemberRole
import infrastructure.BaseE2ETest
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Role derivation through the live Telegram API.
 *
 * [com.ua.astrumon.presentation.util.BotAdminUtils] is the one place where the bot asks Telegram —
 * not the database — who someone is. Integration tests mock it away by definition, so this is the
 * only layer that can catch a `getChatMember` contract change or a status string the mapping does
 * not know about.
 *
 * ## Telegram is its own oracle
 * The expected set comes from `getChatAdministrators`, never from configuration. An earlier version
 * compared against a `TEST_ADMINS` secret; that secret turned out to hold a placeholder id, which
 * nobody had noticed because nothing read it (spovishun-160, findings 10 and 18). Even filled in
 * correctly it would only have deferred the problem — a hand-maintained list drifts the moment
 * someone leaves the chat, and the gate would then fail on stale data rather than on a defect.
 *
 * Cross-checking two independent API paths against each other removes that failure mode entirely:
 * whoever the administrators happen to be, `getChatAdministrators` and `getChatMember` must
 * classify them identically.
 */
class AdminDetectionE2ETest : BaseE2ETest() {
    @Test
    fun `every administrator Telegram lists is resolved as ADMIN`() {
        val admins = chatAdministratorIds()

        val resolved = admins.associateWith { botAdminUtils.getMemberRole(mainBot, testChatId, it) }

        assertTrue(
            resolved.values.all { it == MemberRole.ADMIN },
            "getChatAdministrators and getChatMember disagree — the status mapping is broken. Resolved: $resolved",
        )
    }

    @Test
    fun `start command registers every administrator with the ADMIN role`() {
        val admins = chatAdministratorIds()

        dispatch("/start")

        val registered = allMembers().filter { it.userId in admins }
        assertEquals(
            admins,
            registered.map { it.userId }.toSet(),
            "/start must auto-register every administrator Telegram reports",
        )
        assertTrue(
            registered.all { it.role == MemberRole.ADMIN },
            "Administrators must be stored with the ADMIN role, got: " +
                registered.joinToString { "${it.userId}=${it.role}" },
        )
    }

    /**
     * Administrators of the live test chat, straight from Telegram.
     *
     * Note that Telegram omits other bots from this list, so the helper bot the tests dispatch as
     * never appears here — which is what keeps this consistent with `StartCommand`, which registers
     * from the very same call.
     */
    private fun chatAdministratorIds(): Set<Long> {
        val response = mainBot.getChatAdministrators(ChatId.fromId(testChatId))
        val admins = response.fold({ it }, { error -> error("getChatAdministrators failed: $error") })
        assumeTrue(admins.isNotEmpty(), "Test chat reports no administrators — nothing to verify")
        return admins.map { it.user.id }.toSet()
    }
}
