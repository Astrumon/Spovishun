package com.ua.astrumon.admin.server

import com.ua.astrumon.admin.auth.TokenAuthenticator
import com.ua.astrumon.admin.docker.DockerApiClient
import com.ua.astrumon.admin.docker.DockerResponseMapper
import com.ua.astrumon.admin.docker.LogStreamDeframer
import com.ua.astrumon.admin.docker.RawLogFrame
import com.ua.astrumon.admin.dto.ContainerLogsDto
import com.ua.astrumon.admin.dto.DatabaseHealthDto
import com.ua.astrumon.admin.dto.HealthDto
import com.ua.astrumon.admin.dto.LogLineDto
import com.ua.astrumon.domain.admin.repository.ServerHealthRepository
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.ServerSSESession
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private const val AUTH_PROVIDER = "admin"
private const val DEFAULT_LOG_TAIL = 100
private const val LOG_STREAM_EVENT = "log"

private val streamJson = Json { encodeDefaults = true }
private val logger = LoggerFactory.getLogger("AdminApiRoutes")

/**
 * Installs the admin observability API onto a Ktor [Application].
 *
 * Extracted from [AdminApiServer] so tests can mount it with `testApplication` and mocked
 * dependencies (spovishun-110).
 */
fun Application.adminApiModule(
    token: String,
    dockerClient: DockerApiClient,
    healthRepository: ServerHealthRepository,
) {
    install(ContentNegotiation) {
        json()
    }
    install(SSE)
    install(Authentication) {
        bearer(AUTH_PROVIDER) {
            authenticate { credential ->
                if (TokenAuthenticator.matches(credential.token, token)) UserIdPrincipal(AUTH_PROVIDER) else null
            }
        }
    }
    routing {
        authenticate(AUTH_PROVIDER) {
            route("/api/v1") {
                healthRoute(healthRepository)
                metricsRoute(dockerClient)
                containersRoute(dockerClient)
                logsRoute(dockerClient)
                logsStreamRoute(dockerClient)
            }
        }
    }
}

private fun Route.healthRoute(healthRepository: ServerHealthRepository) {
    get("/health") {
        val dto = healthRepository.check().fold(
            onSuccess = { HealthDto(status = "ok", database = DatabaseHealthDto(connected = true, sizeBytes = it.dbSizeBytes)) },
            onFailure = { HealthDto(status = "degraded", database = DatabaseHealthDto(connected = false, sizeBytes = 0)) },
        )
        call.respond(dto)
    }
}

private fun Route.metricsRoute(dockerClient: DockerApiClient) {
    get("/metrics") {
        val info = dockerClient.info()
        val running = dockerClient.containers().filter { it.state == "running" }
        val stats = running.map { it to dockerClient.stats(it.id) }
        call.respond(DockerResponseMapper.toMetricsDto(info, stats))
    }
}

private fun Route.containersRoute(dockerClient: DockerApiClient) {
    get("/containers") {
        call.respond(DockerResponseMapper.toContainerDtos(dockerClient.containers()))
    }
}

private fun Route.logsRoute(dockerClient: DockerApiClient) {
    get("/containers/{id}/logs") {
        val id = call.parameters["id"]
        if (id.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }
        val tail = call.request.queryParameters["tail"]?.toIntOrNull() ?: DEFAULT_LOG_TAIL
        val logs = DockerResponseMapper.deframeLogs(dockerClient.logs(id, tail))
        call.respond(ContainerLogsDto(containerId = id, tail = tail, logs = logs))
    }
}

private fun Route.logsStreamRoute(dockerClient: DockerApiClient) {
    sse("/containers/{id}/logs/stream") {
        val id = call.parameters["id"]
        if (id.isNullOrBlank()) return@sse
        relayLogs(dockerClient, id)
    }
}

// Relays the live upstream stream until the client disconnects. Cancellation is re-thrown so the
// upstream connection in DockerApiClient.streamLogs is released; other failures are logged, not
// propagated to the bot process.
private suspend fun ServerSSESession.relayLogs(
    dockerClient: DockerApiClient,
    id: String,
) {
    val deframer = LogStreamDeframer()
    try {
        dockerClient.streamLogs(id) { rawBytes ->
            deframer.feed(rawBytes).forEach { frame -> sendLogFrame(frame) }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        logger.warn("Log stream ended with an upstream error", error)
    }
}

private suspend fun ServerSSESession.sendLogFrame(frame: RawLogFrame) {
    frame.payload.split('\n').filter { it.isNotEmpty() }.forEach { rawLine ->
        val dto = DockerResponseMapper.parseLogLine(frame.streamType, rawLine)
        send(ServerSentEvent(event = LOG_STREAM_EVENT, data = streamJson.encodeToString(LogLineDto.serializer(), dto)))
    }
}
