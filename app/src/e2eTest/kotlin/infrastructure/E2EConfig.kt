package infrastructure

import io.github.cdimascio.dotenv.dotenv

/**
 * Telegram-side e2e configuration. Reads the default `.env`; the database half lives in
 * [E2EDbConfig], which reads `.env.e2e`.
 */
object E2EConfig {
    private val env = dotenv { ignoreIfMissing = true }

    private fun get(key: String): String? = env[key]?.takeIf { it.isNotBlank() }

    val mainBotToken: String? = get("TEST_BOT_TOKEN")
    val helperBotToken: String? = get("TEST_HELPER_BOT_TOKEN")
    val testChatId: Long? = get("TEST_CHAT_ID")?.toLongOrNull()

    /**
     * Real administrators of the test chat, used by `AdminDetectionE2ETest` to check role
     * derivation against the live API.
     *
     * Deliberately absent from [isConfigured]: it is supplied in CI but not in every local `.env`,
     * and a developer missing it should lose that one case rather than the whole suite. The tests
     * that need it skip themselves.
     */
    val testAdmins: Set<Long> = get("TEST_ADMINS")
        ?.split(",")
        ?.mapNotNull { it.trim().toLongOrNull() }
        ?.toSet()
        ?: emptySet()

    val isConfigured: Boolean
        get() = mainBotToken != null && helperBotToken != null && testChatId != null
}
