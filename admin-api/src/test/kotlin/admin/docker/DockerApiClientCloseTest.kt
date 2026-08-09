package com.ua.astrumon.admin.docker

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.isActive
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockerApiClientCloseTest {
    @Test
    fun should_closeHttpClient_when_closeCalled() {
        val httpClient = HttpClient(CIO)
        val client = DockerApiClient("http://localhost", httpClient)
        assertTrue(httpClient.isActive)

        client.close()

        assertFalse(httpClient.isActive)
    }
}
