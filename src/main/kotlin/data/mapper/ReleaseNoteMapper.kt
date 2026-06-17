package com.ua.astrumon.data.mapper

import com.ua.astrumon.data.releasenotes.ReleaseNoteDto
import com.ua.astrumon.domain.model.ReleaseNote

fun ReleaseNoteDto.toReleaseNote() = ReleaseNote(
    version = version,
    date = date,
    changes = changes,
)
