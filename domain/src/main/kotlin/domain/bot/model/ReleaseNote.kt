package com.ua.astrumon.domain.bot.model

data class ReleaseNote(
    val version: String,
    val date: String,
    val changes: List<String>,
)
