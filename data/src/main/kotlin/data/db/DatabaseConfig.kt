package com.ua.astrumon.data.db

import io.github.cdimascio.dotenv.dotenv

/**
 * Database connection settings owned by the data layer.
 *
 * Decouples [DatabaseFactory] from the application-level config (`AppConfig` lives in the
 * composition root and bundles Telegram concerns too). The app maps its config into a
 * [DatabaseConfig] at the composition root; the standalone migration dev-tool builds one via
 * [fromEnv].
 */
data class DatabaseConfig(
    val url: String,
    val driver: String,
    val username: String,
    val password: String,
    val poolSize: Int,
) {
    companion object {
        private const val DEFAULT_POOL_SIZE = 10

        /** Reads `<PROFILE>_DATABASE_*` env vars (falling back to `.env`) — used by the migration tool. */
        fun fromEnv(): DatabaseConfig {
            val env = dotenv { ignoreIfMissing = true }
            val prefix = (System.getenv("PROFILE") ?: "dev").uppercase()
            return DatabaseConfig(
                url = env["${prefix}_DATABASE_URL"] ?: "jdbc:postgresql://localhost:5432/spovishun_dev",
                driver = env["${prefix}_DATABASE_DRIVER"] ?: "org.postgresql.Driver",
                username = env["${prefix}_DATABASE_USERNAME"] ?: "postgres",
                password = env["${prefix}_DATABASE_PASSWORD"]
                    ?: error("${prefix}_DATABASE_PASSWORD is not set — refusing to start"),
                poolSize = (env["${prefix}_DATABASE_POOL_SIZE"] ?: "$DEFAULT_POOL_SIZE").toInt(),
            )
        }
    }
}
