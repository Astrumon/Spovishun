package com.ua.astrumon.admin.auth

import java.security.MessageDigest

/**
 * Bearer-token validation for the admin API (spovishun-110).
 *
 * Uses a constant-time comparison ([MessageDigest.isEqual]) to avoid leaking the token length/prefix
 * via timing. The token itself is never logged.
 */
object TokenAuthenticator {
    fun matches(
        provided: String,
        expected: String,
    ): Boolean {
        if (expected.isEmpty()) return false
        return MessageDigest.isEqual(
            provided.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8),
        )
    }
}
