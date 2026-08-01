package com.ua.astrumon.config

import com.ua.astrumon.data.db.DatabaseConfig
import com.ua.astrumon.domain.bot.config.ChatAccessConfig
import com.ua.astrumon.domain.bot.config.ReadinessConfig
import io.github.cdimascio.dotenv.dotenv
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class AppConfig :
    ChatAccessConfig,
    ReadinessConfig {
    private val env = dotenv { ignoreIfMissing = true }

    val telegramBotToken: String = env["TELEGRAM_BOT_TOKEN"]
        ?: error("TELEGRAM_BOT_TOKEN is not set — refusing to start")

    val expectedBotUsername: String? = env["EXPECTED_BOT_USERNAME"]?.takeIf { it.isNotBlank() }

    override val allowedChatIds: Set<Long> = env["ALLOWED_CHAT_IDS"]
        ?.split(",")
        ?.mapNotNull { it.trim().toLongOrNull() }
        ?.toSet()
        .orEmpty()

    override val readinessTtl: Duration = readinessTtlMinutes().minutes

    // DB settings are owned by the data layer; env-reading lives in one place ([DatabaseConfig.fromEnv]).
    val databaseConfig: DatabaseConfig = DatabaseConfig.fromEnv()

    private fun readinessTtlMinutes(): Int {
        val configured = env["READINESS_TTL_MINUTES"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: return DEFAULT_READINESS_TTL_MINUTES
        val minutes = configured.toIntOrNull()
        require(minutes != null && minutes > 0) { "READINESS_TTL_MINUTES must be a positive integer, got '$configured'" }
        return minutes
    }

    private companion object {
        const val DEFAULT_READINESS_TTL_MINUTES = 30
    }
}
