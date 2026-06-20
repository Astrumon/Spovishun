package com.ua.astrumon.domain.bot.model

data class Member(
    val id: Long,
    val userId: Long,
    val username: String,
    val firstName: String,
    val birthday: BirthDate? = null,
)
