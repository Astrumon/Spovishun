package com.ua.astrumon.domain.repository

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.model.MemberRole
import kotlinx.datetime.Instant

interface MemberRepository {
    suspend fun findByUsername(username: String): ResultContainer<Member?>
    suspend fun findByUserId(userId: Long): ResultContainer<Member?>
    suspend fun findByChatIdAndUserId(chatId: Long, userId: Long): ResultContainer<Member?>
    suspend fun save(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        joinedAt: Instant?,
        role: MemberRole = MemberRole.MEMBER
    ): ResultContainer<Member>

    suspend fun updateRole(chatId: Long, userId: Long, role: MemberRole): ResultContainer<Member>
    suspend fun findAll(): ResultContainer<List<Member>>
}