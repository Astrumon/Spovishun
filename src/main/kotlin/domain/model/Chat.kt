package com.ua.astrumon.domain.model

import kotlinx.datetime.Instant

data class Chat(
    val chatId: Long,
    val title: String?,
    val type: String?,
    val registeredAt: Instant
)
