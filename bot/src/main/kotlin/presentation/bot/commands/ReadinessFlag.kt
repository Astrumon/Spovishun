package com.ua.astrumon.presentation.bot.commands

/**
 * `$ready-on` / `$ready-off` — the readiness-mode toggle shared by `/ping <group>` and `/all`,
 * following the `$`-flag convention already used by `/whatsnew $on` and `/register $b`.
 */
internal object ReadinessFlag {
    const val ON = "\$ready-on"
    const val OFF = "\$ready-off"

    /** True to enable, false to disable, null when [token] is not a readiness flag at all. */
    fun parse(token: String?): Boolean? = when (token?.lowercase()) {
        ON -> true
        OFF -> false
        else -> null
    }

    /**
     * A toggle takes no further arguments, so trailing text means the user meant something else —
     * answer with usage rather than silently discarding it (same contract as `/register $b DD.MM`).
     */
    fun isWellFormed(
        args: List<String>,
        flagIndex: Int,
    ): Boolean = args.size == flagIndex + 1
}
