package infrastructure

object E2EConfig {
    val mainBotToken: String? = System.getenv("TEST_BOT_TOKEN")?.takeIf { it.isNotBlank() }
    val helperBotToken: String? = System.getenv("TEST_HELPER_BOT_TOKEN")?.takeIf { it.isNotBlank() }
    val testChatId: Long? = System.getenv("TEST_CHAT_ID")?.takeIf { it.isNotBlank() }?.toLongOrNull()
    val testAdmins: Set<Long> = System.getenv("TEST_ADMINS")
        ?.split(",")
        ?.mapNotNull { it.trim().toLongOrNull() }
        ?.toSet()
        ?: emptySet()

    val isConfigured: Boolean
        get() = mainBotToken != null && helperBotToken != null && testChatId != null
}
