package com.ua.astrumon.admin.config

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv

/**
 * Configuration for the embedded admin observability API (spovishun-110).
 *
 * Mirrors [com.ua.astrumon.data.db.DatabaseConfig.fromEnv] — the module owns its own env reading so
 * the composition root does not need to know the admin-API env contract.
 */
data class AdminApiConfig(
    val enabled: Boolean,
    val bind: String,
    val port: Int,
    val token: String,
    val dockerApiUrl: String,
) {
    companion object {
        private const val DEFAULT_BIND = "127.0.0.1"
        private const val DEFAULT_PORT = 8081
        private const val DEFAULT_DOCKER_API_URL = "http://docker-socket-proxy:2375"

        fun fromEnv(): AdminApiConfig {
            val env = dotenv { ignoreIfMissing = true }
            val enabled = (env["ADMIN_API_ENABLED"] ?: "false").toBoolean()
            return AdminApiConfig(
                enabled = enabled,
                bind = env["ADMIN_API_BIND"] ?: DEFAULT_BIND,
                port = (env["ADMIN_API_PORT"] ?: "$DEFAULT_PORT").toInt(),
                token = resolveToken(env, enabled),
                dockerApiUrl = env["DOCKER_API_URL"] ?: DEFAULT_DOCKER_API_URL,
            )
        }

        // Fail fast: a server that is enabled without a token would accept no requests at all.
        private fun resolveToken(
            env: Dotenv,
            enabled: Boolean,
        ): String {
            val token = env["ADMIN_API_TOKEN"]
            if (enabled) {
                require(!token.isNullOrBlank()) { "ADMIN_API_TOKEN must be set when ADMIN_API_ENABLED=true" }
            }
            return token.orEmpty()
        }
    }
}
