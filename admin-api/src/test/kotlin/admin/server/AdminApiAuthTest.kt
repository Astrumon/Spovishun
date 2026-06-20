package com.ua.astrumon.admin.server

import com.ua.astrumon.admin.docker.DockerApiClient
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.admin.model.ServerHealth
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

class AdminApiAuthTest {
    private val token = "secret-token"
    private val dockerClient = mockk<DockerApiClient>()
    private val healthRepository = mockk<ServerHealthRepository>()

    @BeforeTest
    fun setup() {
        clearAllMocks()
        coEvery { dockerClient.containers() } returns emptyList()
        coEvery { healthRepository.check() } returns ResultContainer.Success(ServerHealth(dbSizeBytes = 1_024))
    }

    @Test
    fun should_return401_when_authorizationHeaderMissing() = testApplication {
        application { adminApiModule(token, dockerClient, healthRepository) }

        val response = client.get("/api/v1/containers")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun should_return401_when_tokenIsWrong() = testApplication {
        application { adminApiModule(token, dockerClient, healthRepository) }

        val response = client.get("/api/v1/containers") {
            header(HttpHeaders.Authorization, "Bearer wrong-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun should_return200_when_tokenIsValidOnContainers() = testApplication {
        application { adminApiModule(token, dockerClient, healthRepository) }

        val response = client.get("/api/v1/containers") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun should_return200_when_tokenIsValidOnHealth() = testApplication {
        application { adminApiModule(token, dockerClient, healthRepository) }

        val response = client.get("/api/v1/health") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }
}
