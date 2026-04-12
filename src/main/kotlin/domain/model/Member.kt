package com.ua.astrumon.domain.model

import kotlinx.datetime.Instant


data class Member(
    val id: Long,
    val chatId: Long,
    val userId: Long,
    val username: String,
    val firstName: String,
    val joinedAt: Instant?,
    val role: MemberRole = MemberRole.MEMBER
)
