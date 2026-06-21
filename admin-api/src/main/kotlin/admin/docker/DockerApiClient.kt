package com.ua.astrumon.admin.docker

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.readRawBytes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.json.Json

/**
 * Read-only client for the Docker Engine API exposed through docker-socket-proxy (spovishun-110).
 *
 * Only GET endpoints permitted by the proxy (`INFO=1`, `CONTAINERS=1`) are called. The [httpClient]
 * is injectable so tests can swap a mock engine; production uses a default CIO client.
 */
class DockerApiClient(
    private val baseUrl: String,
    private val httpClient: HttpClient = defaultClient(),
) {
    suspend fun info(): DockerInfo = httpClient.get("$baseUrl/info").body()

    suspend fun containers(): List<DockerContainer> = httpClient.get("$baseUrl/containers/json?all=true").body()

    suspend fun stats(id: String): DockerStats = httpClient.get("$baseUrl/containers/$id/stats?stream=false").body()

    suspend fun logs(
        id: String,
        tail: Int,
    ): ByteArray = httpClient
        .get("$baseUrl/containers/$id/logs") {
            parameter("stdout", "true")
            parameter("stderr", "true")
            parameter("tail", tail.toString())
        }.readRawBytes()

    /**
     * Relays the live (`follow=true`) container log stream as raw byte chunks (spovishun-111).
     *
     * `tail=0` skips history (the snapshot stays on [logs]); `timestamps=true` prefixes each line so
     * the client can dedup. The upstream connection lives only for the duration of [onChunk] reads —
     * when the calling coroutine is cancelled (client disconnect) the `execute` block unwinds and the
     * connection is released, leaving no dangling upstream connect.
     */
    suspend fun streamLogs(
        id: String,
        onChunk: suspend (rawBytes: ByteArray) -> Unit,
    ) {
        httpClient
            .prepareGet("$baseUrl/containers/$id/logs") {
                parameter("stdout", "true")
                parameter("stderr", "true")
                parameter("follow", "true")
                parameter("timestamps", "true")
                parameter("tail", "0")
            }.execute { response ->
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read > 0) onChunk(buffer.copyOf(read))
                }
            }
    }

    companion object {
        private const val STREAM_BUFFER_SIZE = 8_192

        fun defaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
