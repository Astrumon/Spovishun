package com.ua.astrumon.domain.bot.service

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.ReleaseNote
import com.ua.astrumon.domain.bot.repository.ReleaseNotesRepository

class ReleaseNotesService(
    private val releaseNotesRepository: ReleaseNotesRepository,
) {
    suspend fun getAll(): ResultContainer<List<ReleaseNote>> = releaseNotesRepository.getAll()
}
