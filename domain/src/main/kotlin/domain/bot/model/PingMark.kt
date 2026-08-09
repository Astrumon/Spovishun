package com.ua.astrumon.domain.bot.model

/**
 * What `/ping` repeats after a group's name, once per member (spovishun-180).
 *
 * Three states rather than a nullable emoji: "no custom emoji" and "no emoji at all" are different
 * answers, and collapsing them would make `$mark off` indistinguishable from a group that was never
 * configured. [Hidden] deliberately carries no emoji — the one it was hiding stays in storage, so
 * turning the mark back on restores it rather than resetting it.
 */
sealed interface PingMark {
    /** Fall back to the emoji the chat's language declares. */
    data object Default : PingMark

    /** Render nothing at all. */
    data object Hidden : PingMark

    data class Custom(
        val emoji: String,
    ) : PingMark
}
