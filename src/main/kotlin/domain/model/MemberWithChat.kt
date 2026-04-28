package com.ua.astrumon.domain.model

import kotlinx.datetime.Instant

data class MemberWithChat(
    val id: Long,
    val userId: Long,
    val username: String,
    val firstName: String,
    val role: MemberRole,
    val joinedAt: Instant?,
)
