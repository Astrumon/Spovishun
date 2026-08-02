package com.ua.astrumon.data.bot.releasenotes

import com.ua.astrumon.common.exception.DatabaseException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.data.bot.mapper.toReleaseNote
import com.ua.astrumon.domain.bot.model.BotLanguage
import com.ua.astrumon.domain.bot.model.ReleaseNote
import com.ua.astrumon.domain.bot.repository.ReleaseNotesRepository
import kotlinx.serialization.json.Json

class ReleaseNotesRepositoryImpl : ReleaseNotesRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getAll(language: BotLanguage): ResultContainer<List<ReleaseNote>> = ResultContainer.catching {
        val stream = javaClass.classLoader.getResourceAsStream(RELEASE_NOTES_RESOURCE)
            ?: throw DatabaseException("$RELEASE_NOTES_RESOURCE not found on classpath")
        stream.use { input ->
            json
                .decodeFromString<List<ReleaseNoteDto>>(input.readBytes().decodeToString())
                .map { it.toReleaseNote(language) }
        }
    }

    private companion object {
        const val RELEASE_NOTES_RESOURCE = "release_notes.json"
    }
}
