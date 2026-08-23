package com.ua.astrumon.admin.server

import com.ua.astrumon.admin.docker.DockerApiClient
import com.ua.astrumon.admin.dto.HealthDto
import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.common.util.VersionInfo
import com.ua.astrumon.domain.admin.model.ServerHealth
import com.ua.astrumon.domain.admin.repository.ServerHealthRepository
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the build identity `/health` reports (spovishun-194).
 *
 * The Admin client reads the running bot's version and release date off this endpoint, so both must
 * be answered whatever the database is doing — a degraded response still describes the same process.
 * That the values themselves are current is a separate contract, held by `ReleaseNotesResourceTest`
 * in `:data`, which is the module that can see `release_notes.json`.
 */
class AdminApiHealthTest {
    private val token = "secret-token"
    private val dockerClient = mockk<DockerApiClient>()
    private val healthRepository = mockk<ServerHealthRepository>()

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setup() = clearAllMocks()

    @Test
    fun should_reportBuildIdentity_when_databaseIsUp() = testApplication {
        coEvery { healthRepository.check() } returns ResultContainer.Success(ServerHealth(dbSizeBytes = 1_024))
        application { adminApiModule(token, dockerClient, healthRepository) }

        val body = health()

        assertEquals("ok", body.status)
        assertEquals(VersionInfo.VERSION, body.botVersion)
        assertEquals(VersionInfo.RELEASE_DATE, body.releaseDate)
    }

    @Test
    fun should_reportBuildIdentity_when_databaseIsDown() = testApplication {
        coEvery { healthRepository.check() } returns ResultContainer.Failure(DatabaseException("connection refused"))
        application { adminApiModule(token, dockerClient, healthRepository) }

        val body = health()

        assertEquals("degraded", body.status)
        assertEquals(VersionInfo.VERSION, body.botVersion)
        assertEquals(VersionInfo.RELEASE_DATE, body.releaseDate)
    }

    private suspend fun ApplicationTestBuilder.health(): HealthDto {
        val response = client.get("/api/v1/health") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        return json.decodeFromString(response.bodyAsText())
    }
}
