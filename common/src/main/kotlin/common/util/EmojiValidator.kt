package com.ua.astrumon.common.util

/**
 * Accepts exactly one emoji — the shape `/editg <group> $icon <emoji>` requires (spovishun-32).
 *
 * "One emoji" is checked over code points rather than string length: a single emoji may be several
 * code points long (skin-tone modifier, variation selector, a ZWJ family sequence, a two-symbol
 * flag). Rejecting anything with a second independent emoji is the whole point — `🔥🎯` must fail
 * as loudly as `abc`.
 *
 * Deliberately out of scope: keycap sequences such as `1️⃣`. They start with a plain digit, which
 * would blur the line between an emoji and ordinary text for no real gain.
 */
object EmojiValidator {
    fun isSingleEmoji(input: String): Boolean {
        val codePoints = input.codePoints().toArray()
        if (codePoints.isEmpty() || codePoints.size > MAX_CODE_POINTS) return false
        if (codePoints.any(::isRegionalIndicator)) return isFlag(codePoints)
        return codePoints.all(::isEmojiSequencePart) && rendersAsEmoji(codePoints) && countClusters(codePoints) == 1
    }

    /** A flag is a pair of regional indicators and nothing else — `🇺🇦🔥` is two emoji, not one. */
    private fun isFlag(codePoints: IntArray): Boolean = codePoints.size == FLAG_CODE_POINTS && codePoints.all(::isRegionalIndicator)

    private fun isRegionalIndicator(codePoint: Int): Boolean = codePoint in REGIONAL_INDICATOR_RANGE

    private fun isEmojiSequencePart(codePoint: Int): Boolean = Character.isEmoji(codePoint) ||
        Character.isEmojiComponent(codePoint) ||
        codePoint == ZERO_WIDTH_JOINER ||
        codePoint == VARIATION_SELECTOR_TEXT ||
        codePoint == VARIATION_SELECTOR_EMOJI

    /**
     * Digits, `#` and `*` carry the Emoji property but render as text by default, so a bare `1`
     * would otherwise pass. It counts as an emoji only when the input explicitly asks for emoji
     * presentation — which is also what makes text-default symbols like `❤️` acceptable.
     */
    private fun rendersAsEmoji(codePoints: IntArray): Boolean = codePoints.any { Character.isEmojiPresentation(it) } ||
        codePoints.contains(VARIATION_SELECTOR_EMOJI)

    private fun countClusters(codePoints: IntArray): Int = codePoints.indices.count { startsNewCluster(codePoints, it) }

    /**
     * Modifiers and components attach to the emoji before them, and anything after a ZWJ is joined
     * into the current sequence — neither begins a new emoji.
     */
    private fun startsNewCluster(
        codePoints: IntArray,
        index: Int,
    ): Boolean {
        val codePoint = codePoints[index]
        if (!Character.isEmoji(codePoint)) return false
        if (Character.isEmojiComponent(codePoint) || Character.isEmojiModifier(codePoint)) return false
        return index == 0 || codePoints[index - 1] != ZERO_WIDTH_JOINER
    }

    private const val ZERO_WIDTH_JOINER = 0x200D
    private const val VARIATION_SELECTOR_TEXT = 0xFE0E
    private const val VARIATION_SELECTOR_EMOJI = 0xFE0F
    private const val FLAG_CODE_POINTS = 2

    /** Bounds the value at the `group_settings.icon` column width; no real emoji comes close. */
    private const val MAX_CODE_POINTS = 16

    private val REGIONAL_INDICATOR_RANGE = 0x1F1E6..0x1F1FF
}
