package com.ua.astrumon.config

import io.github.cdimascio.dotenv.dotenv

class AppConfig {
    private val env = dotenv { ignoreIfMissing = true }
    val telegramBotToken: String = env["TELEGRAM_BOT_TOKEN"]

    // PostgreSQL configuration
    val databaseUrl: String = env["DATABASE_URL"] ?: "jdbc:postgresql://localhost:5432/spovishun"
    val databaseDriver: String = env["DATABASE_DRIVER"] ?: "org.postgresql.Driver"
    val databaseUsername: String = env["DATABASE_USERNAME"] ?: "postgres"
    val databasePassword: String = env["DATABASE_PASSWORD"] ?: "password"
}
