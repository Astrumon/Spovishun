package com.ua.astrumon.config

import io.github.cdimascio.dotenv.dotenv

class AppConfig {
    private val profile = System.getenv("PROFILE") ?: "dev"
    private val env = dotenv()
    val telegramBotToken: String = env["TELEGRAM_BOT_TOKEN"]
    val telegramAdminIds: Set<Long> = env["ADMINS"].split(",").map { it.trim().toLong() }.toSet()
    
    // Database configuration (profile-specific prefix: DEV_ or PROD_)
    private val prefix = profile.uppercase()
    val databaseUrl: String = env["${prefix}_DATABASE_URL"] ?: "jdbc:postgresql://localhost:5432/spovishun"
    val databaseDriver: String = env["${prefix}_DATABASE_DRIVER"] ?: "org.postgresql.Driver"
    val databaseUsername: String = env["${prefix}_DATABASE_USERNAME"] ?: "postgres"
    val databasePassword: String = env["${prefix}_DATABASE_PASSWORD"] ?: "password"
    val databasePoolSize: Int = (env["${prefix}_DATABASE_POOL_SIZE"] ?: "10").toInt()
}
