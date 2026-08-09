package com.ua.astrumon.presentation.controller

/**
 * Parameters `/editg` understands. One entry per editable group setting.
 *
 * `off` and `default` are **parameter-level** tokens, not syntax: they mean something only because
 * [ICON] and [MARK] have a state to fall back to. [NAME] has no default to restore, so for it both
 * words are ordinary group names — `$name=off` renames a group to "off". Anything added here decides
 * for itself whether it has reset values, and says so in [GroupSettingsController].
 */
enum class GroupParam(
    val flag: String,
) {
    NAME("\$name"),
    ICON("\$icon"),
    MARK("\$mark"),
    ;

    companion object {
        /** What marks a token as a parameter — and therefore what a group name may not start with. */
        const val PREFIX = "$"

        fun from(token: String): GroupParam? = entries.firstOrNull { it.flag.equals(token, ignoreCase = true) }

        fun supported(): String = entries.joinToString(", ") { it.flag }
    }
}

/**
 * What the tokens after `/editg <group>` turned out to be (spovishun-180).
 *
 * Failures are a nested hierarchy rather than one `Invalid(message)` so the controller renders them
 * — the parser has no `BotMessages` and should not grow one; parsing and phrasing are separate jobs.
 */
sealed interface GroupParamParseResult {
    /** No tokens at all — `/editg <group>` asks to see the settings rather than change them. */
    data object Show : GroupParamParseResult

    data class Edit(
        val values: Map<GroupParam, String>,
    ) : GroupParamParseResult

    sealed interface Failure : GroupParamParseResult {
        /** A token that is not a parameter at all. Silently ignoring it would swallow a typo. */
        data class NotAParameter(
            val token: String,
        ) : Failure

        /** A known parameter written the pre-spovishun-180 way, `$icon 🔥` — the value went missing. */
        data class MissingSeparator(
            val param: GroupParam,
        ) : Failure

        data class UnknownParameter(
            val token: String,
        ) : Failure

        data class DuplicateParameter(
            val param: GroupParam,
        ) : Failure

        /** `$icon=` — the separator is there, the value is not. */
        data class EmptyValue(
            val param: GroupParam,
        ) : Failure
    }
}

/**
 * Turns `$param=value` tokens into a map, or says exactly why it could not (spovishun-180).
 *
 * The rule is one sentence: after the group key, either nothing or every token is a `$param=value`
 * pair. A value ends at whitespace, which is why the old space-separated form no longer parses — and
 * why a parameter whose value needs a space cannot be added without revisiting this.
 *
 * Stateless by design: no dependencies, so it is unit-testable without a single mock.
 */
object GroupParamParser {
    fun parse(tokens: List<String>): GroupParamParseResult {
        if (tokens.isEmpty()) return GroupParamParseResult.Show

        val values = LinkedHashMap<GroupParam, String>()
        tokens.forEach { token ->
            when (val parsed = parseToken(token)) {
                is TokenResult.Failed -> return parsed.failure
                is TokenResult.Parsed ->
                    if (values.put(parsed.param, parsed.value) != null) {
                        return GroupParamParseResult.Failure.DuplicateParameter(parsed.param)
                    }
            }
        }
        return GroupParamParseResult.Edit(values)
    }

    private fun parseToken(token: String): TokenResult {
        if (!token.startsWith(GroupParam.PREFIX)) return TokenResult.Failed(GroupParamParseResult.Failure.NotAParameter(token))

        val separator = token.indexOf(VALUE_SEPARATOR)
        if (separator < 0) return TokenResult.Failed(withoutSeparator(token))

        val flag = token.take(separator)
        val param = GroupParam.from(flag)
        val value = token.substring(separator + 1)
        return when {
            param == null -> TokenResult.Failed(GroupParamParseResult.Failure.UnknownParameter(flag))
            value.isEmpty() -> TokenResult.Failed(GroupParamParseResult.Failure.EmptyValue(param))
            else -> TokenResult.Parsed(param, value)
        }
    }

    /**
     * A recognised parameter that simply lost its `=` gets told so by name. That distinction is the
     * whole mitigation for breaking the old syntax — "unknown parameter $icon" would be a lie.
     */
    private fun withoutSeparator(token: String): GroupParamParseResult.Failure {
        val param = GroupParam.from(token)
        return if (param != null) {
            GroupParamParseResult.Failure.MissingSeparator(param)
        } else {
            GroupParamParseResult.Failure.UnknownParameter(token)
        }
    }

    private const val VALUE_SEPARATOR = '='

    private sealed interface TokenResult {
        data class Parsed(
            val param: GroupParam,
            val value: String,
        ) : TokenResult

        data class Failed(
            val failure: GroupParamParseResult.Failure,
        ) : TokenResult
    }
}
