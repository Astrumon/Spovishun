package com.ua.astrumon.presentation.util

import com.ua.astrumon.common.util.VersionInfo
import com.ua.astrumon.domain.model.ReleaseNote
import com.ua.astrumon.presentation.bot.BotMessages

object ReleaseNotesFormatter {

    fun formatLatest(notes: List<ReleaseNote>): String? {
        val note = notes.firstOrNull() ?: return null
        return buildNoteEntry(note)
    }

    fun formatHistory(notes: List<ReleaseNote>): String = buildString {
        append(BotMessages.WhatsNew.historyTitle)
        notes.forEach { note ->
            append("\n\n")
            append(buildNoteEntry(note))
        }
    }

    private fun buildNoteEntry(note: ReleaseNote): String = buildString {
        append("<b>${VersionInfo.BOT_NAME} v${note.version}</b> (${note.date}):")
        note.changes.forEach { change -> append("\n• $change") }
    }
}
