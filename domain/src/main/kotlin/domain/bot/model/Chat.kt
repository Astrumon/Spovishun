package com.ua.astrumon.domain.bot.model

import kotlinx.datetime.Instant

data class Chat(
    val chatId: Long,
    val title: String?,
    val type: String?,
    val registeredAt: Instant,
    val announcementsEnabled: Boolean = true,
)
