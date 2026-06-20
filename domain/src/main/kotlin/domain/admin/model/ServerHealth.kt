package com.ua.astrumon.domain.admin.model

/**
 * Server health snapshot for the admin observability API (spovishun-110).
 *
 * A successful [com.ua.astrumon.domain.admin.repository.ServerHealthRepository.check] implies the database
 * is reachable; [dbSizeBytes] is the on-disk size of the current database.
 */
data class ServerHealth(
    val dbSizeBytes: Long,
)
