package com.ua.astrumon.config

import io.github.cdimascio.dotenv.dotenv

class AppConfig {
    private val profile = System.getenv("PROFILE") ?: "dev"
    private val env = dotenv { ignoreIfMissing = true }

    val telegramBotToken: String = env["TELEGRAM_BOT_TOKEN"]
        ?: error("TELEGRAM_BOT_TOKEN is not set — refusing to start")

    val expectedBotUsername: String? = env["EXPECTED_BOT_USERNAME"]?.takeIf { it.isNotBlank() }

    val allowedChatIds: Set<Long> = env["ALLOWED_CHAT_IDS"]
        ?.split(",")
        ?.mapNotNull { it.trim().toLongOrNull() }
        ?.toSet()
        .orEmpty()

    private val prefix = profile.uppercase()
    val databaseUrl: String = env["${prefix}_DATABASE_URL"] ?: "jdbc:postgresql://localhost:5432/spovishun_dev"
    val databaseDriver: String = env["${prefix}_DATABASE_DRIVER"] ?: "org.postgresql.Driver"
    val databaseUsername: String = env["${prefix}_DATABASE_USERNAME"] ?: "postgres"
    val databasePassword: String = env["${prefix}_DATABASE_PASSWORD"]
        ?: error("${prefix}_DATABASE_PASSWORD is not set — refusing to start")
    val databasePoolSize: Int = (env["${prefix}_DATABASE_POOL_SIZE"] ?: "10").toInt()
}
