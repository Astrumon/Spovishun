package com.ua.astrumon.domain.admin.repository

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.admin.model.ServerHealth

/**
 * Read-only access to server/database health for the admin observability API (spovishun-110).
 *
 * Keeps Exposed/JDBC out of the `:admin-api` module: the HTTP layer depends on this abstraction,
 * the data layer provides the implementation. A [ResultContainer.Failure] signals the database is
 * unreachable.
 */
interface ServerHealthRepository {
    suspend fun check(): ResultContainer<ServerHealth>
}
