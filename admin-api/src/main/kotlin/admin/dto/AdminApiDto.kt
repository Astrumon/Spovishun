package com.ua.astrumon.admin.dto

import kotlinx.serialization.Serializable

/**
 * Wire contract for the admin observability API (spovishun-110).
 *
 * These DTOs are the deliberate, single source of the JSON contract that the future `spovishun-admin`
 * client will duplicate. Keep them engine-agnostic (no Ktor/Exposed types) and stable.
 *
 * `HealthDto.botVersion` / `releaseDate` come from the build-generated `VersionInfo` (spovishun-194),
 * so they read the same whatever `status` says — which build is running does not depend on whether
 * the database answered. `releaseDate` is `unknown` for a version bumped ahead of its
 * `release_notes.json` entry.
 */

@Serializable
data class HealthDto(
    val status: String,
    val database: DatabaseHealthDto,
    val botVersion: String,
    val releaseDate: String,
)

@Serializable
data class DatabaseHealthDto(
    val connected: Boolean,
    val sizeBytes: Long,
)

@Serializable
data class ContainerDto(
    val id: String,
    val name: String,
    val image: String,
    val state: String,
    val status: String,
)

@Serializable
data class ContainerLogsDto(
    val containerId: String,
    val tail: Int,
    val logs: String,
)

/**
 * One live log line streamed over SSE by `GET /containers/{id}/logs/stream` (spovishun-111).
 *
 * Wire shape per SSE message: `event: log`, `data:` = this DTO as JSON. [ts] is the RFC3339
 * timestamp Docker emits with `timestamps=true`; the client uses it to merge the tail snapshot with
 * the live stream without duplicates or gaps. [stream] is `"stdout"` or `"stderr"`.
 */
@Serializable
data class LogLineDto(
    val ts: String,
    val stream: String,
    val line: String,
)

@Serializable
data class MetricsDto(
    val serverVersion: String,
    val operatingSystem: String,
    val cpuCount: Int,
    val memoryTotalBytes: Long,
    val containersTotal: Int,
    val containersRunning: Int,
    val containersStopped: Int,
    val imagesCount: Int,
    val containers: List<ContainerStatsDto>,
)

@Serializable
data class ContainerStatsDto(
    val id: String,
    val name: String,
    val memoryUsageBytes: Long,
    val cpuPercent: Double,
)
