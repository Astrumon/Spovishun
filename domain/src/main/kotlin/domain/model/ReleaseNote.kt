package com.ua.astrumon.domain.model

data class ReleaseNote(
    val version: String,
    val date: String,
    val changes: List<String>,
)
