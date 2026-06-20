package com.ua.astrumon.data.bot.mapper

import com.ua.astrumon.data.bot.releasenotes.ReleaseNoteDto
import com.ua.astrumon.domain.bot.model.ReleaseNote

fun ReleaseNoteDto.toReleaseNote() = ReleaseNote(
    version = version,
    date = date,
    changes = changes,
)
