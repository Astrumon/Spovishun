package com.ua.astrumon.domain.bot.model

/**
 * One field of a partial update: either absent from the request, or carrying a new value.
 *
 * `T?` cannot express this on its own — for a field that is itself nullable, null already means
 * "clear it", and there would be no value left to mean "the caller never mentioned this".
 */
sealed interface Patch<out T> {
    data object Untouched : Patch<Nothing>

    data class Value<out T>(
        val value: T,
    ) : Patch<T>
}

/**
 * Everything `/editg` can change about a group in one call (spovishun-180).
 *
 * Untouched by default, so a caller states only what it edits and every field it stays silent about
 * is left alone. The whole patch is written in a single transaction — a partially applied edit is
 * not a state this command ever produces.
 */
data class GroupSettingsPatch(
    /** The group's name is also its key: renaming rewrites the identifier users type in `/ping`. */
    val name: Patch<String> = Patch.Untouched,
    /** Null clears the icon rendered before the group's name. */
    val icon: Patch<String?> = Patch.Untouched,
    val pingMark: Patch<PingMark> = Patch.Untouched,
)
