package com.ua.astrumon.domain.repository

import com.ua.astrumon.common.result.ResultContainer

interface BotMetaRepository {
    suspend fun get(key: String): ResultContainer<String?>
    suspend fun set(key: String, value: String): ResultContainer<Unit>
}
