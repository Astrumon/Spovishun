package com.ua.astrumon.admin.docker

import com.ua.astrumon.domain.admin.model.DockerClientException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.net.ConnectException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

/**
 * Maps Ktor CIO transport exceptions onto the domain [DockerClientException] hierarchy (spovishun-143).
 *
 * Pure and side-effect free so the classification is unit-testable. This object is the single place
 * where `io.ktor.*` / `java.nio.*` transport types are inspected — keeping them out of the routing
 * layer. Non-2xx responses are classified in [DockerApiClient] from the response status, not here.
 */
object DockerErrorMapper {
    fun map(throwable: Throwable): DockerClientException = when (throwable) {
        // Timeout types first: Ktor's ConnectTimeoutException extends java.net.ConnectException, so the
        // Unreachable branch below would otherwise swallow it.
        is ConnectTimeoutException,
        is SocketTimeoutException,
        is HttpRequestTimeoutException,
        -> DockerClientException.Timeout(throwable)

        is UnresolvedAddressException,
        is UnknownHostException,
        is ConnectException,
        -> DockerClientException.Unreachable(throwable)

        else -> DockerClientException.Unknown(throwable)
    }
}
