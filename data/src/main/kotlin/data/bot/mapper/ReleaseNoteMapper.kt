package com.ua.astrumon.data.bot.mapper

import com.ua.astrumon.data.bot.releasenotes.ReleaseNoteDto
import com.ua.astrumon.domain.bot.model.BotLanguage
import com.ua.astrumon.domain.bot.model.ReleaseNote

/**
 * Resolves the record down to a single language.
 *
 * A **missing** key falls back to Ukrainian — releases are authored in it — so a chat on a language
 * a record was never translated into still gets the release instead of silence.
 *
 * An **empty list is not the same as a missing key** and is returned as-is: it is the explicit
 * "announce nothing in this language" (spovishun-152), the per-language counterpart of the empty
 * `changes` object that suppresses a release everywhere. `ReleaseNotesResourceTest` is what keeps
 * the two apart in practice, by failing on a record that omits a supported language outright.
 */
fun ReleaseNoteDto.toReleaseNote(language: BotLanguage) = ReleaseNote(
    version = version,
    date = date,
    changes = changes[language.code] ?: changes[BotLanguage.UK.code].orEmpty(),
)
