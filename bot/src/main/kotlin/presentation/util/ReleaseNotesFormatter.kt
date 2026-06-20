package com.ua.astrumon.presentation.util

import com.ua.astrumon.common.util.VersionInfo
import com.ua.astrumon.domain.bot.model.ReleaseNote
import com.ua.astrumon.presentation.bot.BotMessages

object ReleaseNotesFormatter {
    // An entry with no changes is an internal-only release (e.g. v1.6.0): it must produce no
    // announcement and no /whatsnew reply, so callers treat null as "nothing to show" (spovishun-134).
    fun formatLatest(notes: List<ReleaseNote>): String? {
        val note = notes.firstOrNull() ?: return null
        if (note.changes.isEmpty()) return null
        return buildNoteEntry(note)
    }

    fun formatHistory(notes: List<ReleaseNote>): String? {
        val renderable = notes.filter { it.changes.isNotEmpty() }
        if (renderable.isEmpty()) return null
        return buildString {
            append(BotMessages.WhatsNew.historyTitle)
            renderable.forEach { note ->
                append("\n\n")
                append(buildNoteEntry(note))
            }
        }
    }

    private fun buildNoteEntry(note: ReleaseNote): String = buildString {
        append("<b>${VersionInfo.BOT_NAME} v${note.version}</b> (${note.date}):")
        note.changes.forEach { change -> append("\n• $change") }
    }
}
