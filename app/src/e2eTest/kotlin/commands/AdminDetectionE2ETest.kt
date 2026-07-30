package commands

import com.ua.astrumon.domain.bot.model.MemberRole
import infrastructure.BaseE2ETest
import infrastructure.E2EConfig
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Role derivation through the live Telegram API.
 *
 * [com.ua.astrumon.presentation.util.BotAdminUtils] is the one place where the bot asks Telegram —
 * not the database — who someone is. Integration tests mock it away by definition, so this is the
 * only layer that can catch a `getChatMember` contract change or a status string the mapping does
 * not know about.
 *
 * Gated on `TEST_ADMINS` per test rather than in [E2EConfig.isConfigured]: the variable names the
 * real administrators of the test chat, it is supplied in CI, and a developer without it should
 * lose this case only — not the whole suite.
 */
class AdminDetectionE2ETest : BaseE2ETest() {
    /**
     * Asserts over the ids Telegram *and* `TEST_ADMINS` agree on, rather than demanding that every
     * configured id still be an administrator. The secret is maintained by hand and drifts when
     * someone leaves the chat; the contract under test is the mapping from Telegram's `status`
     * string to [MemberRole], and an intersection proves that without coupling the gate to secret
     * hygiene. An empty intersection still fails — that would mean the mapping resolved nobody.
     */
    @Test
    fun `chat administrators are resolved as ADMIN through the real API`() {
        val admins = E2EConfig.testAdmins
        assumeTrue(admins.isNotEmpty(), "TEST_ADMINS not set — skipping real admin-detection check")

        val resolved = admins.associateWith { botAdminUtils.getMemberRole(mainBot, testChatId, it) }
        val recognised = resolved.filterValues { it == MemberRole.ADMIN }.keys

        assertTrue(
            recognised.isNotEmpty(),
            "Telegram reported none of TEST_ADMINS ($admins) as an admin of the test chat — " +
                "either the role mapping is broken or the secret no longer matches the chat. Resolved: $resolved",
        )
    }

    @Test
    fun `start command registers the real chat administrators with the ADMIN role`() {
        val admins = E2EConfig.testAdmins
        assumeTrue(admins.isNotEmpty(), "TEST_ADMINS not set — skipping real admin-detection check")

        dispatch("/start")

        val registeredAdmins = allMembers().filter { it.userId in admins }

        assertTrue(registeredAdmins.isNotEmpty(), "/start must auto-register the chat administrators")
        assertTrue(
            registeredAdmins.all { it.role == MemberRole.ADMIN },
            "Every administrator /start registered must be stored with the ADMIN role, got: " +
                registeredAdmins.joinToString { "${it.userId}=${it.role}" },
        )
    }
}
