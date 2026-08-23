package com.ua.astrumon.admin.server

import com.ua.astrumon.admin.docker.DockerApiClient
import com.ua.astrumon.admin.docker.DockerContainer
import com.ua.astrumon.admin.docker.DockerResponseMapper
import com.ua.astrumon.admin.docker.DockerStats
import com.ua.astrumon.admin.docker.LogStreamDeframer
import com.ua.astrumon.admin.docker.RawLogFrame
import com.ua.astrumon.admin.dto.ContainerLogsDto
import com.ua.astrumon.admin.dto.DatabaseHealthDto
import com.ua.astrumon.admin.dto.HealthDto
import com.ua.astrumon.admin.dto.LogLineDto
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.common.util.VersionInfo
import com.ua.astrumon.domain.admin.repository.ServerHealthRepository
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
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

private const val DEFAULT_LOG_TAIL = 100
private const val LOG_STREAM_EVENT = "log"

private val streamJson = Json { encodeDefaults = true }
private val logger = LoggerFactory.getLogger("AdminApiRoutes")

/**
 * Installs the admin observability API onto a Ktor [Application].
 *
 * Extracted from [AdminApiServer] so tests can mount it with `testApplication` and mocked
 * dependencies (spovishun-110). Docker client failures are surfaced as [DockerClientException] and
 * mapped to HTTP status codes; [StatusPages] is a safety net for anything that still escapes routing
 * (spovishun-143).
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
    installErrorMapping()
    installBearerAuth(token)
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
            onSuccess = { healthDto("ok", DatabaseHealthDto(connected = true, sizeBytes = it.dbSizeBytes)) },
            onFailure = { healthDto("degraded", DatabaseHealthDto(connected = false, sizeBytes = 0)) },
        )
        call.respond(dto)
    }
}

/**
 * Stamps every `/health` answer with the running build. Version and release date are the same on the
 * `ok` and `degraded` paths — they describe the process, not the database — so they are filled here
 * rather than repeated in both `fold` branches (spovishun-194).
 */
private fun healthDto(
    status: String,
    database: DatabaseHealthDto,
) = HealthDto(
    status = status,
    database = database,
    botVersion = VersionInfo.VERSION,
    releaseDate = VersionInfo.RELEASE_DATE,
)

private fun Route.metricsRoute(dockerClient: DockerApiClient) {
    get("/metrics") {
        val result = dockerClient.info().flatMap { info ->
            dockerClient.containers().flatMap { containers ->
                val running = containers.filter { it.state == "running" }
                collectStats(dockerClient, running).map { stats ->
                    DockerResponseMapper.toMetricsDto(info, stats)
                }
            }
        }
        call.respondResult(result)
    }
}

private fun Route.containersRoute(dockerClient: DockerApiClient) {
    get("/containers") {
        val result = dockerClient.containers().map { DockerResponseMapper.toContainerDtos(it) }
        call.respondResult(result)
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
        val result = dockerClient.logs(id, tail).map { rawBytes ->
            ContainerLogsDto(containerId = id, tail = tail, logs = DockerResponseMapper.deframeLogs(rawBytes))
        }
        call.respondResult(result)
    }
}

private fun Route.logsStreamRoute(dockerClient: DockerApiClient) {
    sse("/containers/{id}/logs/stream") {
        val id = call.parameters["id"]
        if (id.isNullOrBlank()) return@sse
        relayLogs(dockerClient, id)
    }
}

// Fetches per-container stats one by one, short-circuiting on the first failure so a single
// unreachable stats call surfaces as one domain error rather than a partial result.
private suspend fun collectStats(
    dockerClient: DockerApiClient,
    running: List<DockerContainer>,
): ResultContainer<List<Pair<DockerContainer, DockerStats>>> {
    val collected = mutableListOf<Pair<DockerContainer, DockerStats>>()
    for (container in running) {
        when (val stats = dockerClient.stats(container.id)) {
            is ResultContainer.Success -> collected.add(container to stats.data)
            is ResultContainer.Failure -> return stats
        }
    }
    return ResultContainer.success(collected)
}

// Resolves a client result into a response: the DTO on success, the mapped status on failure.
private suspend inline fun <reified T : Any> ApplicationCall.respondResult(result: ResultContainer<T>) {
    result.fold(
        onSuccess = { respond(it) },
        onFailure = { respond(it.toHttpStatus()) },
    )
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
