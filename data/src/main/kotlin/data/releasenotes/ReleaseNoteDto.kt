package com.ua.astrumon.data.releasenotes

import kotlinx.serialization.Serializable

@Serializable
data class ReleaseNoteDto(
    val version: String,
    val date: String,
    val changes: List<String>,
)
