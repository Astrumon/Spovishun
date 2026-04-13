package com.ua.astrumon.data.db.repository

import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.data.db.safeDbQuery
import com.ua.astrumon.data.db.table.Members
import com.ua.astrumon.data.mapper.toMember
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.repository.MemberRepository
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

class MemberRepositoryImpl : MemberRepository {
    override suspend fun findById(id: Long): ResultContainer<Member?> =
        safeDbQuery {
            Members.selectAll()
                .where { Members.id eq id }
                .singleOrNull()?.toMember()
        }

    override suspend fun findByUserId(userId: Long): ResultContainer<Member?> =
        safeDbQuery {
            Members.selectAll()
                .where { Members.userId eq userId }
                .singleOrNull()?.toMember()
        }

    override suspend fun findByUsername(username: String): ResultContainer<Member?> =
        safeDbQuery {
            Members.selectAll()
                .where { Members.username eq username }
                .singleOrNull()?.toMember()
        }

    override suspend fun saveOrUpdate(userId: Long, username: String, firstName: String): ResultContainer<Member> =
        safeDbQuery {
            Members.upsert(Members.userId) {
                it[Members.userId] = userId
                it[Members.username] = username
                it[Members.firstname] = firstName
            }
            Members.selectAll()
                .where { Members.userId eq userId }
                .singleOrNull()?.toMember()
                ?: throw ResourceNotFoundException("Member", userId.toString())
        }
}
