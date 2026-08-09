package com.ua.astrumon.admin.server

import com.ua.astrumon.admin.config.AdminApiConfig
import com.ua.astrumon.admin.docker.DockerApiClient
import com.ua.astrumon.domain.admin.repository.ServerHealthRepository
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import org.slf4j.LoggerFactory

/**
 * Embedded Ktor (CIO) server hosting the read-only admin observability API (spovishun-110).
 *
 * Started from the composition root alongside Telegram long-polling and bound to the configured
 * interface only (tailnet IP in prod). Lifecycle is owned here; routing lives in the Ktor
 * `Application` module [adminApiModule].
 */
class AdminApiServer(
    private val config: AdminApiConfig,
    private val dockerClient: DockerApiClient,
    private val healthRepository: ServerHealthRepository,
) {
    private val logger = LoggerFactory.getLogger(AdminApiServer::class.java)
    private var server: EmbeddedServer<*, *>? = null

    fun start() {
        logger.info("Starting admin API on {}:{}", config.bind, config.port)
        server = embeddedServer(CIO, host = config.bind, port = config.port) {
            adminApiModule(config.token, dockerClient, healthRepository)
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(GRACE_PERIOD_MILLIS, SHUTDOWN_TIMEOUT_MILLIS)
        server = null
    }

    companion object {
        private const val GRACE_PERIOD_MILLIS = 1_000L
        private const val SHUTDOWN_TIMEOUT_MILLIS = 5_000L
    }
}
