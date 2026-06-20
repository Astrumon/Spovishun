package com.ua.astrumon.data.admin.repository

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.data.db.safeDbQuery
import com.ua.astrumon.domain.admin.model.ServerHealth
import com.ua.astrumon.domain.admin.repository.ServerHealthRepository
import org.jetbrains.exposed.sql.transactions.TransactionManager

class ServerHealthRepositoryImpl : ServerHealthRepository {
    // PostgreSQL-specific: on-disk size of the current database. Connectivity is implied — the query
    // executes inside safeDbQuery, so an unreachable DB surfaces as ResultContainer.Failure.
    override suspend fun check(): ResultContainer<ServerHealth> = safeDbQuery {
        val sizeBytes = TransactionManager
            .current()
            .exec("SELECT pg_database_size(current_database()) AS size") { rs ->
                if (rs.next()) rs.getLong("size") else 0L
            } ?: 0L
        ServerHealth(dbSizeBytes = sizeBytes)
    }
}
