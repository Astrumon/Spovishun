package com.ua.astrumon.domain.repository

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.MemberChat
import com.ua.astrumon.domain.model.MemberRole
import kotlinx.datetime.Instant

interface MemberChatRepository {
    suspend fun findByMemberIdAndChatId(memberId: Long, chatId: Long): ResultContainer<MemberChat?>
    suspend fun save(memberId: Long, chatId: Long, role: MemberRole, joinedAt: Instant?): ResultContainer<MemberChat>
    suspend fun updateRole(memberId: Long, chatId: Long, role: MemberRole): ResultContainer<MemberChat>
    suspend fun findAllByChatId(chatId: Long): ResultContainer<List<MemberChat>>
    suspend fun existsByMemberIdAndChatId(memberId: Long, chatId: Long): ResultContainer<Boolean>
}
