package com.ua.astrumon.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ReleaseNote(
    val version: String,
    val date: String,
    val changes: List<String>,
)
