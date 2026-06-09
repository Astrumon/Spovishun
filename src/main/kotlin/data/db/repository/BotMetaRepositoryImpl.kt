package com.ua.astrumon.data.db.repository

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.data.db.safeDbQuery
import com.ua.astrumon.data.db.table.BotMeta
import com.ua.astrumon.domain.repository.BotMetaRepository
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

class BotMetaRepositoryImpl : BotMetaRepository {
    override suspend fun get(key: String): ResultContainer<String?> =
        safeDbQuery {
            BotMeta
                .selectAll()
                .where { BotMeta.key eq key }
                .singleOrNull()
                ?.get(BotMeta.value)
        }

    override suspend fun set(
        key: String,
        value: String,
    ): ResultContainer<Unit> =
        safeDbQuery {
            BotMeta.upsert(BotMeta.key) {
                it[BotMeta.key] = key
                it[BotMeta.value] = value
                it[BotMeta.updatedAt] = Clock.System.now()
            }
            Unit
        }
}
