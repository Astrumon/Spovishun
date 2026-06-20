package com.ua.astrumon.admin.docker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DockerResponseMapperTest {
    @Test
    fun should_stripLeadingSlash_when_mappingContainerName() {
        val container = DockerContainer(
            id = "abc123",
            names = listOf("/spovishun-bot"),
            image = "ghcr.io/astrumon/spovishun:latest",
            state = "running",
            status = "Up 3 hours",
        )

        val dto = DockerResponseMapper.toContainerDto(container)

        assertEquals("abc123", dto.id)
        assertEquals("spovishun-bot", dto.name)
        assertEquals("ghcr.io/astrumon/spovishun:latest", dto.image)
        assertEquals("running", dto.state)
        assertEquals("Up 3 hours", dto.status)
    }

    @Test
    fun should_returnEmptyName_when_containerHasNoNames() {
        val dto = DockerResponseMapper.toContainerDto(DockerContainer(id = "x"))

        assertEquals("", dto.name)
    }

    @Test
    fun should_computeCpuPercent_when_deltasArePositive() {
        val stats = DockerStats(
            cpuStats = DockerCpuStats(
                cpuUsage = DockerCpuUsage(totalUsage = 2_000),
                systemCpuUsage = 10_000,
                onlineCpus = 2,
            ),
            preCpuStats = DockerCpuStats(
                cpuUsage = DockerCpuUsage(totalUsage = 1_000),
                systemCpuUsage = 5_000,
            ),
        )

        // (1000 / 5000) * 2 cpus * 100 = 40.0
        assertEquals(40.0, DockerResponseMapper.cpuPercent(stats), 0.0001)
    }

    @Test
    fun should_returnZeroCpuPercent_when_systemDeltaIsZero() {
        val stats = DockerStats(
            cpuStats = DockerCpuStats(cpuUsage = DockerCpuUsage(totalUsage = 100), systemCpuUsage = 5_000),
            preCpuStats = DockerCpuStats(cpuUsage = DockerCpuUsage(totalUsage = 50), systemCpuUsage = 5_000),
        )

        assertEquals(0.0, DockerResponseMapper.cpuPercent(stats), 0.0001)
    }

    @Test
    fun should_buildMetrics_when_givenInfoAndStats() {
        val info = DockerInfo(
            serverVersion = "27.0.0",
            operatingSystem = "Ubuntu 22.04",
            ncpu = 4,
            memTotal = 8_000_000_000,
            containers = 3,
            containersRunning = 2,
            containersStopped = 1,
            images = 12,
        )
        val container = DockerContainer(id = "c1", names = listOf("/bot"))
        val stats = DockerStats(memoryStats = DockerMemoryStats(usage = 256_000_000))

        val dto = DockerResponseMapper.toMetricsDto(info, listOf(container to stats))

        assertEquals("27.0.0", dto.serverVersion)
        assertEquals(4, dto.cpuCount)
        assertEquals(2, dto.containersRunning)
        assertEquals(1, dto.containers.size)
        assertEquals("bot", dto.containers.first().name)
        assertEquals(256_000_000, dto.containers.first().memoryUsageBytes)
    }

    @Test
    fun should_deframe_when_logStreamHasFrameHeaders() {
        // header [stream=1, 0, 0, 0, size=0,0,0,5] + "hello"
        val frame = byteArrayOf(1, 0, 0, 0, 0, 0, 0, 5) + "hello".toByteArray()

        assertEquals("hello", DockerResponseMapper.deframeLogs(frame))
    }

    @Test
    fun should_concatenate_when_logStreamHasMultipleFrames() {
        val first = byteArrayOf(1, 0, 0, 0, 0, 0, 0, 5) + "hello".toByteArray()
        val second = byteArrayOf(2, 0, 0, 0, 0, 0, 0, 6) + " world".toByteArray()

        assertEquals("hello world", DockerResponseMapper.deframeLogs(first + second))
    }

    @Test
    fun should_returnRawText_when_logStreamIsNotFramed() {
        val raw = "plain log line".toByteArray()

        assertEquals("plain log line", DockerResponseMapper.deframeLogs(raw))
    }

    @Test
    fun should_returnEmpty_when_logStreamIsEmpty() {
        assertTrue(DockerResponseMapper.deframeLogs(ByteArray(0)).isEmpty())
    }
}
