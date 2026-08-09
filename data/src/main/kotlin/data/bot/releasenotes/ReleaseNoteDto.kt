package com.ua.astrumon.data.bot.releasenotes

import kotlinx.serialization.Serializable

/**
 * One `release_notes.json` record.
 *
 * [changes] is keyed by `BotLanguage.code`, so a release is authored once and every translation
 * ships inside the same record — a version can never exist in one language and be silently missing
 * from another. An empty map is the internal-only release (spovishun-134): it renders nothing and
 * suppresses the broadcast.
 */
@Serializable
data class ReleaseNoteDto(
    val version: String,
    val date: String,
    val changes: Map<String, List<String>>,
)
