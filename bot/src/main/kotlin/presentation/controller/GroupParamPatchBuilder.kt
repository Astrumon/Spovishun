package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.util.EmojiValidator
import com.ua.astrumon.domain.bot.model.GroupSettingsPatch
import com.ua.astrumon.domain.bot.model.Patch
import com.ua.astrumon.domain.bot.model.PingMark
import com.ua.astrumon.presentation.bot.BotMessages

/** Either a patch every value of which validated, or the message saying which one did not. */
sealed interface PatchResult {
    data class Built(
        val patch: GroupSettingsPatch,
    ) : PatchResult

    data class Invalid(
        val message: String,
    ) : PatchResult
}

/**
 * Turns parsed `$param=value` pairs into a validated [GroupSettingsPatch] (spovishun-182).
 *
 * Lifted out of [GroupSettingsController] when `/newgroup` gained the same parameters: `$icon` and
 * `$mark` must reach [EmojiValidator] by one route, not by one per command — two routes drift, and
 * the one that drifts is the one nobody remembers to change.
 *
 * Stateless like [GroupParamParser], and for the same reason: it decides what a value *means*, never
 * how the answer is phrased or where it is stored.
 */
object GroupParamPatchBuilder {
    /**
     * Every value is validated before a single one is written — a patch is applied whole or not at
     * all, so a typo in the second parameter must not leave the first one already stored.
     */
    fun build(
        values: Map<GroupParam, String>,
        messages: BotMessages,
    ): PatchResult {
        val invalid = values.firstNotNullOfOrNull { (param, raw) -> validationError(param, raw, messages) }
        if (invalid != null) {
            return PatchResult.Invalid(invalid)
        }

        return PatchResult.Built(
            GroupSettingsPatch(
                name = values[GroupParam.NAME].patch { it.lowercase() },
                icon = values[GroupParam.ICON].patch(::iconValue),
                pingMark = values[GroupParam.MARK].patch(::markValue),
            ),
        )
    }

    private fun validationError(
        param: GroupParam,
        raw: String,
        messages: BotMessages,
    ): String? = when (param) {
        GroupParam.NAME -> messages.group.nameInvalid.takeUnless { isValidName(raw) }
        GroupParam.ICON -> messages.group.iconInvalid.takeUnless { raw.isReset(OFF) || EmojiValidator.isSingleEmoji(raw) }
        GroupParam.MARK ->
            messages.group.markInvalid
                .takeUnless { raw.isReset(OFF) || raw.isReset(DEFAULT) || EmojiValidator.isSingleEmoji(raw) }
    }

    /** `$` would make the name unaddressable — the parser would read it back as a parameter. */
    private fun isValidName(raw: String): Boolean = raw.length <= NAME_MAX_LENGTH && !raw.startsWith(GroupParam.PREFIX)

    private fun iconValue(raw: String): String? = raw.takeUnless { it.isReset(OFF) }

    private fun markValue(raw: String): PingMark = when {
        raw.isReset(OFF) -> PingMark.Hidden
        raw.isReset(DEFAULT) -> PingMark.Default
        else -> PingMark.Custom(raw)
    }

    private fun <T> String?.patch(transform: (String) -> T): Patch<T> = this?.let { Patch.Value(transform(it)) } ?: Patch.Untouched

    private fun String.isReset(token: String): Boolean = equals(token, ignoreCase = true)

    /** Clears a parameter that has nothing to fall back to, and hides one that does. */
    private const val OFF = "off"

    /** Restores a parameter to the value the chat's language declares. */
    private const val DEFAULT = "default"

    /** Matches the `groups.name` column width. */
    private const val NAME_MAX_LENGTH = 64
}
