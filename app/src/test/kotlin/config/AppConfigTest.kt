package config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppConfigTest {
    private fun parseAllowedChatIds(value: String?): Set<Long> = value
        ?.split(",")
        ?.mapNotNull { it.trim().toLongOrNull() }
        ?.toSet()
        .orEmpty()

    @Test
    fun `parseAllowedChatIds should return empty set for null`() {
        assertTrue(parseAllowedChatIds(null).isEmpty())
    }

    @Test
    fun `parseAllowedChatIds should return empty set for blank string`() {
        assertTrue(parseAllowedChatIds("").isEmpty())
    }

    @Test
    fun `parseAllowedChatIds should parse single chat id`() {
        assertEquals(setOf(12345L), parseAllowedChatIds("12345"))
    }

    @Test
    fun `parseAllowedChatIds should parse multiple chat ids`() {
        assertEquals(setOf(111L, 222L, 333L), parseAllowedChatIds("111,222,333"))
    }

    @Test
    fun `parseAllowedChatIds should trim whitespace around ids`() {
        assertEquals(setOf(111L, 222L), parseAllowedChatIds(" 111 , 222 "))
    }

    @Test
    fun `parseAllowedChatIds should skip non-numeric garbage values`() {
        assertEquals(setOf(111L, 333L), parseAllowedChatIds("111,garbage,333"))
    }

    @Test
    fun `parseAllowedChatIds should return empty set when all values are garbage`() {
        assertTrue(parseAllowedChatIds("abc,def,ghi").isEmpty())
    }

    @Test
    fun `parseAllowedChatIds should handle negative chat ids`() {
        assertEquals(setOf(-100123456789L), parseAllowedChatIds("-100123456789"))
    }

    @Test
    fun `parseAllowedChatIds should deduplicate ids`() {
        assertEquals(setOf(111L), parseAllowedChatIds("111,111,111"))
    }
}
