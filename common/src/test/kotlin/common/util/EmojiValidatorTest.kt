package com.ua.astrumon.common.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmojiValidatorTest {
    @Test
    fun `should accept when input is a single plain emoji`() {
        assertTrue(EmojiValidator.isSingleEmoji("🔥"))
        assertTrue(EmojiValidator.isSingleEmoji("🎯"))
        assertTrue(EmojiValidator.isSingleEmoji("🦀"))
    }

    @Test
    fun `should accept when emoji carries a skin-tone modifier`() {
        assertTrue(EmojiValidator.isSingleEmoji("👍🏽"))
    }

    @Test
    fun `should accept when emoji is a ZWJ sequence`() {
        assertTrue(EmojiValidator.isSingleEmoji("👨‍👩‍👧‍👦"))
        assertTrue(EmojiValidator.isSingleEmoji("👩‍💻"))
    }

    @Test
    fun `should accept when text-default symbol carries the emoji variation selector`() {
        assertTrue(EmojiValidator.isSingleEmoji("❤️"))
    }

    @Test
    fun `should accept when input is a two-symbol flag`() {
        assertTrue(EmojiValidator.isSingleEmoji("🇺🇦"))
    }

    @Test
    fun `should reject when input holds two independent emoji`() {
        assertFalse(EmojiValidator.isSingleEmoji("🔥🎯"))
        assertFalse(EmojiValidator.isSingleEmoji("👍🏽🔥"))
    }

    @Test
    fun `should reject when input mixes an emoji with text`() {
        assertFalse(EmojiValidator.isSingleEmoji("🔥a"))
        assertFalse(EmojiValidator.isSingleEmoji("a🔥"))
        assertFalse(EmojiValidator.isSingleEmoji("🔥 "))
    }

    @Test
    fun `should reject when input is plain text`() {
        assertFalse(EmojiValidator.isSingleEmoji("abc"))
        assertFalse(EmojiValidator.isSingleEmoji("off"))
    }

    @Test
    fun `should reject when input is empty or blank`() {
        assertFalse(EmojiValidator.isSingleEmoji(""))
        assertFalse(EmojiValidator.isSingleEmoji(" "))
    }

    @Test
    fun `should reject when input is a bare digit that merely carries the emoji property`() {
        assertFalse(EmojiValidator.isSingleEmoji("1"))
        assertFalse(EmojiValidator.isSingleEmoji("#"))
        assertFalse(EmojiValidator.isSingleEmoji("1️⃣"))
    }

    @Test
    fun `should reject when flag symbols do not form exactly one pair`() {
        assertFalse(EmojiValidator.isSingleEmoji("🇺"))
        assertFalse(EmojiValidator.isSingleEmoji("🇺🇦🇬🇧"))
        assertFalse(EmojiValidator.isSingleEmoji("🇺🇦🔥"))
    }

    @Test
    fun `should reject when input is longer than the icon column allows`() {
        assertFalse(EmojiValidator.isSingleEmoji("🔥".repeat(20)))
    }
}
