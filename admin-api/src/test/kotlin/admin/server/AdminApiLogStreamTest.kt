package com.ua.astrumon.admin.server

import com.ua.astrumon.admin.docker.DockerApiClient
import com.ua.astrumon.domain.admin.repository.ServerHealthRepository
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.testApplication
import io.ktor.sse.ServerSentEvent
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminApiLogStreamTest {
    private val token = "secret-token"
    private val dockerClient = mockk<DockerApiClient>()
    private val healthRepository = mockk<ServerHealthRepository>()

    @BeforeTest
    fun setup() {
        clearAllMocks()
    }

    // header [stream=1, 0, 0, 0, size] + "<ts> <line>"
    private fun stdoutFrame(text: String): ByteArray {
        val payload = text.toByteArray()
        val size = payload.size
        val header = byteArrayOf(
            1,
            0,
            0,
            0,
            (size ushr 24).toByte(),
            (size ushr 16).toByte(),
            (size ushr 8).toByte(),
            size.toByte(),
        )
        return header + payload
    }

    @Test
    fun should_emitLogEvent_when_upstreamStreamsFramedLine() = testApplication {
        application { adminApiModule(token, dockerClient, healthRepository) }
        coEvery { dockerClient.streamLogs(any(), any()) } coAnswers {
            val onChunk = secondArg<suspend (ByteArray) -> Unit>()
            onChunk(stdoutFrame("2026-06-21T10:00:01Z hello-live\n"))
        }
        val sseClient = createClient { install(SSE) }

        val events = mutableListOf<ServerSentEvent>()
        sseClient.sse(
            "/api/v1/containers/abc/logs/stream",
            request = { header(HttpHeaders.Authorization, "Bearer $token") },
        ) {
            events += incoming.take(1).toList()
        }

        assertEquals(1, events.size)
        assertEquals("log", events.first().event)
        val data = events.first().data.orEmpty()
        assertTrue(data.contains("\"stream\":\"stdout\""))
        assertTrue(data.contains("\"line\":\"hello-live\""))
        assertTrue(data.contains("\"ts\":\"2026-06-21T10:00:01Z\""))
    }
}
