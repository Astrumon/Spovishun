package infrastructure

import io.github.cdimascio.dotenv.dotenv

object E2EDbConfig {
    private val env = dotenv {
        filename = ".env.e2e"
        ignoreIfMissing = true
    }

    private fun get(key: String): String? = env[key]?.takeIf { it.isNotBlank() }

    val databaseUrl: String? = get("E2E_DATABASE_URL")
    val databaseDriver: String = get("E2E_DATABASE_DRIVER") ?: "org.postgresql.Driver"
    val databaseUsername: String = get("E2E_DATABASE_USERNAME") ?: "postgres"
    val databasePassword: String = get("E2E_DATABASE_PASSWORD") ?: ""
    val databasePoolSize: Int = get("E2E_DATABASE_POOL_SIZE")?.toIntOrNull() ?: 2

    val isConfigured: Boolean
        get() = databaseUrl != null && E2EConfig.isConfigured
}
