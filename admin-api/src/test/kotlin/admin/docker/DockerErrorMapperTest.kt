package com.ua.astrumon.admin.docker

import com.ua.astrumon.domain.admin.model.DockerClientException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class DockerErrorMapperTest {
    @Test
    fun should_mapToUnreachable_when_addressUnresolved() {
        val error = UnresolvedAddressException()

        val mapped = DockerErrorMapper.map(error)

        assertIs<DockerClientException.Unreachable>(mapped)
        assertSame(error, mapped.cause)
    }

    @Test
    fun should_mapToUnreachable_when_unknownHost() {
        assertIs<DockerClientException.Unreachable>(DockerErrorMapper.map(UnknownHostException("docker-socket-proxy")))
    }

    @Test
    fun should_mapToUnreachable_when_connectionRefused() {
        assertIs<DockerClientException.Unreachable>(DockerErrorMapper.map(ConnectException("Connection refused")))
    }

    @Test
    fun should_mapToTimeout_when_connectTimeout() {
        assertIs<DockerClientException.Timeout>(DockerErrorMapper.map(ConnectTimeoutException("connect timed out")))
    }

    @Test
    fun should_mapToTimeout_when_requestTimeout() {
        assertIs<DockerClientException.Timeout>(DockerErrorMapper.map(HttpRequestTimeoutException("http://proxy/info", 1_000)))
    }

    @Test
    fun should_mapToUnknown_when_unclassifiedError() {
        assertIs<DockerClientException.Unknown>(DockerErrorMapper.map(IOException("boom")))
    }
}
