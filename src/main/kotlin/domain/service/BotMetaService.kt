package com.ua.astrumon.domain.service

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.repository.BotMetaRepository

class BotMetaService(private val botMetaRepository: BotMetaRepository) {

    private companion object {
        const val LAST_NOTIFIED_VERSION_KEY = "last_notified_version"
    }

    suspend fun getLastNotifiedVersion(): ResultContainer<String?> =
        botMetaRepository.get(LAST_NOTIFIED_VERSION_KEY)

    suspend fun setLastNotifiedVersion(version: String): ResultContainer<Unit> =
        botMetaRepository.set(LAST_NOTIFIED_VERSION_KEY, version)
}
