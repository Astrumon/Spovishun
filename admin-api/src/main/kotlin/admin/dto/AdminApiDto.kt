package com.ua.astrumon.admin.dto

import kotlinx.serialization.Serializable

/**
 * Wire contract for the admin observability API (spovishun-110).
 *
 * These DTOs are the deliberate, single source of the JSON contract that the future `spovishun-admin`
 * client will duplicate. Keep them engine-agnostic (no Ktor/Exposed types) and stable.
 */

@Serializable
data class HealthDto(
    val status: String,
    val database: DatabaseHealthDto,
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
