package com.ua.astrumon.admin.docker

import com.ua.astrumon.admin.dto.ContainerDto
import com.ua.astrumon.admin.dto.ContainerStatsDto
import com.ua.astrumon.admin.dto.LogLineDto
import com.ua.astrumon.admin.dto.MetricsDto

/**
 * Pure mapping from raw Docker Engine API models to the admin-API wire contract.
 *
 * No I/O — every function is deterministic and unit-tested in isolation (spovishun-110).
 */
object DockerResponseMapper {
    private const val CPU_PERCENT_SCALE = 100.0
    private const val STREAM_TYPE_STDERR = 2
    private const val STREAM_STDOUT = "stdout"
    private const val STREAM_STDERR = "stderr"

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
        while (offset + LogFrameHeader.HEADER_SIZE <= raw.size && LogFrameHeader.isHeaderAt(raw, offset)) {
            val size = LogFrameHeader.sizeAt(raw, offset)
            val start = offset + LogFrameHeader.HEADER_SIZE
            val end = (start + size).coerceAtMost(raw.size)
            builder.append(String(raw, start, end - start, Charsets.UTF_8))
            offset = end
        }
        if (offset < raw.size) {
            builder.append(String(raw, offset, raw.size - offset, Charsets.UTF_8))
        }
        return builder.toString()
    }

    /**
     * Parses one de-framed log line into the [LogLineDto] wire shape (spovishun-111).
     *
     * With `timestamps=true` Docker prefixes each line with an RFC3339 timestamp and a single space.
     * A line without that prefix still relays with an empty [LogLineDto.ts] rather than being dropped.
     */
    fun parseLogLine(
        streamType: Int,
        payloadLine: String,
    ): LogLineDto {
        val trimmed = payloadLine.removeSuffix("\r")
        val spaceIndex = trimmed.indexOf(' ')
        val ts = if (spaceIndex > 0) trimmed.substring(0, spaceIndex) else ""
        val line = if (spaceIndex > 0) trimmed.substring(spaceIndex + 1) else trimmed
        val stream = if (streamType == STREAM_TYPE_STDERR) STREAM_STDERR else STREAM_STDOUT
        return LogLineDto(ts = ts, stream = stream, line = line)
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
}
