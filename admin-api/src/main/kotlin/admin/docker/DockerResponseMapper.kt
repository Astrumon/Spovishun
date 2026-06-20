package com.ua.astrumon.admin.docker

import com.ua.astrumon.admin.dto.ContainerDto
import com.ua.astrumon.admin.dto.ContainerStatsDto
import com.ua.astrumon.admin.dto.MetricsDto

/**
 * Pure mapping from raw Docker Engine API models to the admin-API wire contract.
 *
 * No I/O — every function is deterministic and unit-tested in isolation (spovishun-110).
 */
object DockerResponseMapper {
    private const val LOG_FRAME_HEADER_SIZE = 8
    private const val MAX_STREAM_TYPE = 2
    private const val CPU_PERCENT_SCALE = 100.0

    fun toContainerDto(container: DockerContainer): ContainerDto = ContainerDto(
        id = container.id,
        name = containerName(container.names),
        image = container.image,
        state = container.state,
        status = container.status,
    )

    fun toContainerDtos(containers: List<DockerContainer>): List<ContainerDto> = containers.map(::toContainerDto)

    fun toMetricsDto(
        info: DockerInfo,
        stats: List<Pair<DockerContainer, DockerStats>>,
    ): MetricsDto = MetricsDto(
        serverVersion = info.serverVersion,
        operatingSystem = info.operatingSystem,
        cpuCount = info.ncpu,
        memoryTotalBytes = info.memTotal,
        containersTotal = info.containers,
        containersRunning = info.containersRunning,
        containersStopped = info.containersStopped,
        imagesCount = info.images,
        containers = stats.map { (container, stat) -> toContainerStatsDto(container, stat) },
    )

    fun cpuPercent(stats: DockerStats): Double {
        val cpuDelta = stats.cpuStats.cpuUsage.totalUsage - stats.preCpuStats.cpuUsage.totalUsage
        val systemDelta = stats.cpuStats.systemCpuUsage - stats.preCpuStats.systemCpuUsage
        val cpus = stats.cpuStats.onlineCpus.coerceAtLeast(1)
        return if (cpuDelta > 0 && systemDelta > 0) {
            (cpuDelta.toDouble() / systemDelta.toDouble()) * cpus * CPU_PERCENT_SCALE
        } else {
            0.0
        }
    }

    /**
     * De-multiplexes a Docker log stream into plain text.
     *
     * With TTY disabled, Docker frames each chunk with an 8-byte header
     * `[streamType, 0, 0, 0, size(uint32 big-endian)]`. With TTY enabled (or via some proxies) the
     * payload is raw — trailing un-framed bytes are appended as-is.
     */
    fun deframeLogs(raw: ByteArray): String {
        if (raw.isEmpty()) return ""
        val builder = StringBuilder()
        var offset = 0
        while (offset + LOG_FRAME_HEADER_SIZE <= raw.size && isFrameHeader(raw, offset)) {
            val size = readFrameSize(raw, offset)
            val start = offset + LOG_FRAME_HEADER_SIZE
            val end = (start + size).coerceAtMost(raw.size)
            builder.append(String(raw, start, end - start, Charsets.UTF_8))
            offset = end
        }
        if (offset < raw.size) {
            builder.append(String(raw, offset, raw.size - offset, Charsets.UTF_8))
        }
        return builder.toString()
    }

    private fun containerName(names: List<String>): String = names.firstOrNull()?.removePrefix("/").orEmpty()

    private fun toContainerStatsDto(
        container: DockerContainer,
        stats: DockerStats,
    ): ContainerStatsDto = ContainerStatsDto(
        id = container.id,
        name = containerName(container.names),
        memoryUsageBytes = stats.memoryStats.usage,
        cpuPercent = cpuPercent(stats),
    )

    // A valid frame header has a known stream type in byte 0 and zero padding in bytes 1..3.
    private fun isFrameHeader(
        raw: ByteArray,
        offset: Int,
    ): Boolean = (raw[offset].toInt() and 0xFF) <= MAX_STREAM_TYPE &&
        raw[offset + 1].toInt() == 0 &&
        raw[offset + 2].toInt() == 0 &&
        raw[offset + 3].toInt() == 0

    private fun readFrameSize(
        raw: ByteArray,
        offset: Int,
    ): Int = ((raw[offset + 4].toInt() and 0xFF) shl 24) or
        ((raw[offset + 5].toInt() and 0xFF) shl 16) or
        ((raw[offset + 6].toInt() and 0xFF) shl 8) or
        (raw[offset + 7].toInt() and 0xFF)
}
