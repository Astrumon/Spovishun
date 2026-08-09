package com.ua.astrumon.domain.bot.model

data class Group(
    val id: Long,
    val chatId: Long,
    val name: String,
    val memberUsernames: List<String>,
    val readinessEnabled: Boolean = true,
    val icon: String? = null,
    val pingMark: PingMark = PingMark.Default,
)
