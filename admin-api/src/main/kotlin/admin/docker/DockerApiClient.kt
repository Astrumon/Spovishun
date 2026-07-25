package com.ua.astrumon.admin.docker

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.admin.model.DockerClientException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Read-only client for the Docker Engine API exposed through docker-socket-proxy (spovishun-110).
 *
 * Only GET endpoints permitted by the proxy (`INFO=1`, `CONTAINERS=1`) are called. The [httpClient]
 * is injectable so tests can swap a mock engine; production uses a default CIO client.
 *
 * Every call is wrapped in [ResultContainer]: transport failures (unresolved host, refused
 * connection, timeouts) are mapped to the domain [DockerClientException] via [DockerErrorMapper], and
 * non-2xx responses become [DockerClientException.HttpError] (spovishun-143). This keeps HTTP-engine
 * exception types from leaking past the client into routing.
 */
class DockerApiClient(
    private val baseUrl: String,
    private val httpClient: HttpClient = defaultClient(),
) {
    suspend fun info(): ResultContainer<DockerInfo> {
        val url = "$baseUrl/info"
        return execute(url, { httpClient.get(url) }) { it.body() }
    }

    suspend fun containers(): ResultContainer<List<DockerContainer>> {
        val url = "$baseUrl/containers/json?all=true"
        return execute(url, { httpClient.get(url) }) { it.body() }
    }

    suspend fun stats(id: String): ResultContainer<DockerStats> {
        val url = "$baseUrl/containers/$id/stats?stream=false"
        return execute(url, { httpClient.get(url) }) { it.body() }
    }

    suspend fun logs(
        id: String,
        tail: Int,
    ): ResultContainer<ByteArray> {
        val url = "$baseUrl/containers/$id/logs"
        return execute(
            url,
            {
                httpClient.get(url) {
                    parameter("stdout", "true")
                    parameter("stderr", "true")
                    parameter("tail", tail.toString())
                }
            },
        ) { it.readRawBytes() }
    }

    /**
     * Relays the live (`follow=true`) container log stream as raw byte chunks (spovishun-111).
     *
     * `tail=0` skips history (the snapshot stays on [logs]); `timestamps=true` prefixes each line so
     * the client can dedup. The upstream connection lives only for the duration of [onChunk] reads —
     * when the calling coroutine is cancelled (client disconnect) the `execute` block unwinds and the
     * connection is released, leaving no dangling upstream connect. Transport failures surface to the
     * caller ([relayLogs][com.ua.astrumon.admin.server]), which logs and ends the stream.
     */
    suspend fun streamLogs(
        id: String,
        onChunk: suspend (rawBytes: ByteArray) -> Unit,
    ) {
        val url = "$baseUrl/containers/$id/logs"
        logger.info("Docker API GET {} (stream)", url)
        httpClient
            .prepareGet(url) {
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

    // Logs the target URL (root-cause diagnostics: empty/invalid host, docker name off the compose
    // network), runs the request, then classifies the outcome: 2xx -> success; non-2xx -> HttpError;
    // transport throw -> mapped domain error. Cancellation is re-thrown so structured concurrency is
    // preserved.
    private suspend fun <T> execute(
        url: String,
        request: suspend () -> HttpResponse,
        onSuccess: suspend (HttpResponse) -> T,
    ): ResultContainer<T> {
        logger.info("Docker API GET {}", url)
        return try {
            val response = request()
            if (response.status.isSuccess()) {
                ResultContainer.success(onSuccess(response))
            } else {
                ResultContainer.failure(DockerClientException.HttpError(response.status.value))
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            ResultContainer.failure(DockerErrorMapper.map(error))
        }
    }

    companion object {
        private const val STREAM_BUFFER_SIZE = 8_192
        private val logger = LoggerFactory.getLogger(DockerApiClient::class.java)

        fun defaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
