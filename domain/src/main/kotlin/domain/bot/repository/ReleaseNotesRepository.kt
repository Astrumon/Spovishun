package com.ua.astrumon.domain.bot.repository

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.BotLanguage
import com.ua.astrumon.domain.bot.model.ReleaseNote

interface ReleaseNotesRepository {
    /** Notes already resolved to [language]; entries with no changes in it come back empty, not absent. */
    suspend fun getAll(language: BotLanguage): ResultContainer<List<ReleaseNote>>
}
