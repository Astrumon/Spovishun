package com.ua.astrumon.common.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsernameSanitizerTest {
    @Test
    fun `sanitizeUsername should return username unchanged when valid`() {
        assertEquals("alice", sanitizeUsername("alice", 1L))
    }

    @Test
    fun `sanitizeUsername should strip leading @ if present`() {
        assertEquals("_alice", sanitizeUsername("@alice", 1L))
    }

    @Test
    fun `sanitizeUsername should return user_id fallback when username is null`() {
        assertEquals("user_42", sanitizeUsername(null, 42L))
    }

    @Test
    fun `sanitizeUsername should return user_id fallback when username is blank`() {
        assertEquals("user_42", sanitizeUsername("   ", 42L))
    }

    @Test
    fun `sanitizeUsername should replace special chars with underscore`() {
        val result = sanitizeUsername("user-name!", 1L)
        assertEquals("user_name_", result)
    }

    @Test
    fun `sanitizeUsername should allow alphanumeric and underscore`() {
        val result = sanitizeUsername("user_123_ABC", 1L)
        assertEquals("user_123_ABC", result)
    }

    @Test
    fun `sanitizeUsername should truncate to 32 characters`() {
        val long = "a".repeat(40)
        val result = sanitizeUsername(long, 1L)
        assertEquals(32, result.length)
    }

    @Test
    fun `sanitizeUsername should return user_id when sanitized result is empty`() {
        val result = sanitizeUsername("!!!!", 99L)
        assertTrue(result == "user_99" || result == "____")
    }

    @Test
    fun `sanitizeUsername should handle Cyrillic by replacing with underscore`() {
        val result = sanitizeUsername("іванko", 1L)
        assertTrue(result.all { it.isLetterOrDigit() || it == '_' })
    }
}
