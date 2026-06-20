package com.ua.astrumon.domain.bot.model

import kotlinx.datetime.Instant

data class MemberChat(
    val memberId: Long,
    val chatId: Long,
    val role: MemberRole,
    val joinedAt: Instant?,
)
