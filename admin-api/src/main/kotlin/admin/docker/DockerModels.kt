package com.ua.astrumon.admin.docker

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Raw Docker Engine API response models (read-only subset exposed by docker-socket-proxy).
 *
 * Internal to the docker package — never leaves the module. The public wire contract is the `dto/`
 * package; [DockerResponseMapper] converts these into it. All fields default so partial/extra JSON
 * (the proxy hides much of the Engine API) deserializes cleanly with `ignoreUnknownKeys`.
 */

@Serializable
data class DockerContainer(
    @SerialName("Id") val id: String = "",
    @SerialName("Names") val names: List<String> = emptyList(),
    @SerialName("Image") val image: String = "",
    @SerialName("State") val state: String = "",
    @SerialName("Status") val status: String = "",
)

@Serializable
data class DockerInfo(
    @SerialName("ServerVersion") val serverVersion: String = "",
    @SerialName("OperatingSystem") val operatingSystem: String = "",
    @SerialName("NCPU") val ncpu: Int = 0,
    @SerialName("MemTotal") val memTotal: Long = 0,
    @SerialName("Containers") val containers: Int = 0,
    @SerialName("ContainersRunning") val containersRunning: Int = 0,
    @SerialName("ContainersStopped") val containersStopped: Int = 0,
    @SerialName("Images") val images: Int = 0,
)

@Serializable
data class DockerStats(
    @SerialName("cpu_stats") val cpuStats: DockerCpuStats = DockerCpuStats(),
    @SerialName("precpu_stats") val preCpuStats: DockerCpuStats = DockerCpuStats(),
    @SerialName("memory_stats") val memoryStats: DockerMemoryStats = DockerMemoryStats(),
)

@Serializable
data class DockerCpuStats(
    @SerialName("cpu_usage") val cpuUsage: DockerCpuUsage = DockerCpuUsage(),
    @SerialName("system_cpu_usage") val systemCpuUsage: Long = 0,
    @SerialName("online_cpus") val onlineCpus: Int = 0,
)

@Serializable
data class DockerCpuUsage(
    @SerialName("total_usage") val totalUsage: Long = 0,
)

@Serializable
data class DockerMemoryStats(
    @SerialName("usage") val usage: Long = 0,
    @SerialName("limit") val limit: Long = 0,
)
