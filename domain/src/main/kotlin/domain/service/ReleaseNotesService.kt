package com.ua.astrumon.domain.service

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.ReleaseNote
import com.ua.astrumon.domain.repository.ReleaseNotesRepository

class ReleaseNotesService(
    private val releaseNotesRepository: ReleaseNotesRepository,
) {
    suspend fun getAll(): ResultContainer<List<ReleaseNote>> = releaseNotesRepository.getAll()
}
