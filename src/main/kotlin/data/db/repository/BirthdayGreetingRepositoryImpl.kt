package com.ua.astrumon.data.db.repository

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.data.db.safeDbQuery
import com.ua.astrumon.data.db.table.BirthdayGreetings
import com.ua.astrumon.domain.repository.BirthdayGreetingRepository
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

class BirthdayGreetingRepositoryImpl : BirthdayGreetingRepository {
    override suspend fun wasSent(memberId: Long, year: Int): ResultContainer<Boolean> =
        safeDbQuery {
            BirthdayGreetings.selectAll()
                .where { (BirthdayGreetings.memberId eq memberId) and (BirthdayGreetings.year eq year) }
                .any()
        }

    override suspend fun markSent(memberId: Long, year: Int, sentAt: Instant): ResultContainer<Unit> =
        safeDbQuery {
            BirthdayGreetings.upsert(BirthdayGreetings.memberId, BirthdayGreetings.year) {
                it[BirthdayGreetings.memberId] = memberId
                it[BirthdayGreetings.year] = year
                it[BirthdayGreetings.sentAt] = sentAt
            }
            Unit
        }
}
