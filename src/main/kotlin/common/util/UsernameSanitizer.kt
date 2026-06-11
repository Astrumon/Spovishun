package com.ua.astrumon.common.util

fun sanitizeUsername(
    username: String?,
    userId: Long,
): String {
    if (username.isNullOrBlank()) return "user_$userId"
    val sanitized = username.trim().replace(Regex("[^a-zA-Z0-9_]"), "_").take(32)
    return sanitized.ifEmpty { "user_$userId" }
}
