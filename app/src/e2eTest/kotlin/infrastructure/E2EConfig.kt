package infrastructure

import io.github.cdimascio.dotenv.dotenv

object E2EConfig {
    private val env = dotenv { ignoreIfMissing = true }

    private fun get(key: String): String? = env[key]?.takeIf { it.isNotBlank() }

    val mainBotToken: String? = get("TEST_BOT_TOKEN")
    val helperBotToken: String? = get("TEST_HELPER_BOT_TOKEN")
    val testChatId: Long? = get("TEST_CHAT_ID")?.toLongOrNull()
    val testAdmins: Set<Long> = get("TEST_ADMINS")
        ?.split(",")
        ?.mapNotNull { it.trim().toLongOrNull() }
        ?.toSet()
        ?: emptySet()

    val isConfigured: Boolean
        get() = mainBotToken != null && helperBotToken != null && testChatId != null
}
