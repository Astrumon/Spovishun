package com.ua.astrumon.domain.bot.model

enum class MemberRole { MEMBER, MODERATOR, ADMIN }

fun MemberRole.badge(): String = when (this) {
    MemberRole.ADMIN -> " 🔐"
    MemberRole.MODERATOR -> " 🛡"
    MemberRole.MEMBER -> ""
}
