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

    // There is deliberately no TEST_ADMINS here. Administrator expectations come from
    // getChatAdministrators at run time — see AdminDetectionE2ETest. A configured list can only
    // drift out of sync with the chat, and the one that existed held a placeholder id for long
    // enough that nothing ever caught it (spovishun-160).

    val isConfigured: Boolean
        get() = mainBotToken != null && helperBotToken != null && testChatId != null
}
