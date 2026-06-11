package com.ua.astrumon.domain.repository

import com.ua.astrumon.common.result.ResultContainer
import kotlinx.datetime.Instant

interface BirthdayGreetingRepository {
    suspend fun wasSent(
        memberId: Long,
        year: Int,
    ): ResultContainer<Boolean>

    suspend fun markSent(
        memberId: Long,
        year: Int,
        sentAt: Instant,
    ): ResultContainer<Unit>
}
