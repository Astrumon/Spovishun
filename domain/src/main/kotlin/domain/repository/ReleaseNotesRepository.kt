package com.ua.astrumon.domain.repository

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.ReleaseNote

interface ReleaseNotesRepository {
    suspend fun getAll(): ResultContainer<List<ReleaseNote>>
}
