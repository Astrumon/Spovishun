package com.ua.astrumon.domain.service

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.ReleaseNote
import kotlinx.serialization.json.Json

class ReleaseNotesService {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getAll(): ResultContainer<List<ReleaseNote>> =
        ResultContainer.catching {
            val stream = ReleaseNotesService::class.java.classLoader
                .getResourceAsStream("release_notes.json")
                ?: throw DatabaseException("release_notes.json not found on classpath")
            stream.use { json.decodeFromString<List<ReleaseNote>>(it.readBytes().decodeToString()) }
        }
}
