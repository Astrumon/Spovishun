package com.ua.astrumon.domain.model

enum class MemberRole { MEMBER, MODERATOR, ADMIN }

fun MemberRole.badge(): String = when (this) {
    MemberRole.ADMIN -> " 🔐"
    MemberRole.MODERATOR -> " 🛡"
    MemberRole.MEMBER -> ""
}
