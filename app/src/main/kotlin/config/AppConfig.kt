package com.ua.astrumon.config

import com.ua.astrumon.data.db.DatabaseConfig
import com.ua.astrumon.domain.bot.config.ChatAccessConfig
import io.github.cdimascio.dotenv.dotenv

class AppConfig : ChatAccessConfig {
    private val env = dotenv { ignoreIfMissing = true }

    val telegramBotToken: String = env["TELEGRAM_BOT_TOKEN"]
        ?: error("TELEGRAM_BOT_TOKEN is not set — refusing to start")

    val expectedBotUsername: String? = env["EXPECTED_BOT_USERNAME"]?.takeIf { it.isNotBlank() }

    override val allowedChatIds: Set<Long> = env["ALLOWED_CHAT_IDS"]
        ?.split(",")
        ?.mapNotNull { it.trim().toLongOrNull() }
        ?.toSet()
        .orEmpty()

    // DB settings are owned by the data layer; env-reading lives in one place ([DatabaseConfig.fromEnv]).
    val databaseConfig: DatabaseConfig = DatabaseConfig.fromEnv()
}
