package com.ua.astrumon.admin.server

import com.ua.astrumon.admin.auth.TokenAuthenticator
import com.ua.astrumon.admin.docker.DockerApiClient
import com.ua.astrumon.admin.docker.DockerResponseMapper
import com.ua.astrumon.admin.dto.ContainerLogsDto
import com.ua.astrumon.admin.dto.DatabaseHealthDto
import com.ua.astrumon.admin.dto.HealthDto
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

private const val AUTH_PROVIDER = "admin"
private const val DEFAULT_LOG_TAIL = 100

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
