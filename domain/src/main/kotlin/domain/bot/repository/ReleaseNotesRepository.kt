package com.ua.astrumon.domain.bot.repository

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.ReleaseNote

interface ReleaseNotesRepository {
    suspend fun getAll(): ResultContainer<List<ReleaseNote>>
}
