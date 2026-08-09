package com.ua.astrumon.admin.server

import com.ua.astrumon.admin.docker.DockerApiClient
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.admin.model.DockerClientException
import com.ua.astrumon.domain.admin.repository.ServerHealthRepository
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminApiErrorMappingTest {
    private val token = "secret-token"
    private val dockerClient = mockk<DockerApiClient>()
    private val healthRepository = mockk<ServerHealthRepository>()

    @BeforeTest
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun should_return503_when_metricsProxyUnreachable() = testApplication {
        application { adminApiModule(token, dockerClient, healthRepository) }
        coEvery { dockerClient.info() } returns ResultContainer.Failure(DockerClientException.Unreachable())

        val response = client.get("/api/v1/metrics") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }

    @Test
    fun should_return503_when_containersProxyUnreachable() = testApplication {
        application { adminApiModule(token, dockerClient, healthRepository) }
        coEvery { dockerClient.containers() } returns ResultContainer.Failure(DockerClientException.Unreachable())

        val response = client.get("/api/v1/containers") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }

    @Test
    fun should_return504_when_metricsProxyTimesOut() = testApplication {
        application { adminApiModule(token, dockerClient, healthRepository) }
        coEvery { dockerClient.info() } returns ResultContainer.Failure(DockerClientException.Timeout())

        val response = client.get("/api/v1/metrics") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.GatewayTimeout, response.status)
    }

    @Test
    fun should_propagateUpstreamStatus_when_containersReturnHttpError() = testApplication {
        application { adminApiModule(token, dockerClient, healthRepository) }
        coEvery { dockerClient.containers() } returns ResultContainer.Failure(DockerClientException.HttpError(NOT_FOUND))

        val response = client.get("/api/v1/containers") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    private companion object {
        const val NOT_FOUND = 404
    }
}
