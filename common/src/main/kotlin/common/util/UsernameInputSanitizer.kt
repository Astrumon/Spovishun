package com.ua.astrumon.common.util

object UsernameInputSanitizer {
    private const val MAX_USERNAME_LENGTH = 32
    private val VALID_USERNAME_REGEX = Regex("^[a-zA-Z0-9_]{1,${MAX_USERNAME_LENGTH}}$")
    private val NON_USERNAME_CHAR_REGEX = Regex("[^a-zA-Z0-9_]")

    data class ParseResult(
        val valid: List<String>,
        val invalid: List<String>,
    )

    /**
     * Coerces a Telegram-supplied username into a safe display form, falling back to `user_<id>`
     * when there is nothing usable left. Unlike [normalizeUsername] this never returns null — the
     * caller needs something to render.
     */
    fun sanitizeUsername(
        username: String?,
        userId: Long,
    ): String {
        if (username.isNullOrBlank()) return "user_$userId"
        return username
            .trim()
            .replace(NON_USERNAME_CHAR_REGEX, "_")
            .take(MAX_USERNAME_LENGTH)
    }

    fun normalizeUsername(input: String): String? {
        val cleaned = input.trim().removePrefix("@").lowercase()
        if (cleaned.isEmpty()) return null
        return cleaned.takeIf { VALID_USERNAME_REGEX.matches(it) }
    }

    fun parseUsernames(rawInput: String): ParseResult {
        val tokens = rawInput
            .split(',', ' ', '\t', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val valid = LinkedHashSet<String>()
        val invalid = mutableListOf<String>()

        for (token in tokens) {
            val normalized = normalizeUsername(token)
            if (normalized != null) valid.add(normalized) else invalid.add(token)
        }
        return ParseResult(valid.toList(), invalid)
    }
}
