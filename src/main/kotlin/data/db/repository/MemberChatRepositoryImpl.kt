package com.ua.astrumon.data.db.repository

import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.data.db.safeDbQuery
import com.ua.astrumon.data.db.table.MemberChats
import com.ua.astrumon.data.mapper.toMemberChat
import com.ua.astrumon.domain.model.MemberChat
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.repository.MemberChatRepository
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

class MemberChatRepositoryImpl : MemberChatRepository {
    override suspend fun findByMemberIdAndChatId(memberId: Long, chatId: Long): ResultContainer<MemberChat?> =
        safeDbQuery {
            MemberChats.selectAll()
                .where { memberChatPredicate(memberId, chatId) }
                .singleOrNull()?.toMemberChat()
        }

    override suspend fun save(memberId: Long, chatId: Long, role: MemberRole, joinedAt: Instant?): ResultContainer<MemberChat> =
        safeDbQuery {
            MemberChats.upsert(MemberChats.memberId, MemberChats.chatId) {
                it[MemberChats.memberId] = memberId
                it[MemberChats.chatId] = chatId
                it[MemberChats.role] = role.name
                it[MemberChats.joinedAt] = joinedAt
            }
            MemberChats.selectAll()
                .where { memberChatPredicate(memberId, chatId) }
                .singleOrNull()?.toMemberChat()
                ?: throw ResourceNotFoundException("MemberChat", "$memberId/$chatId")
        }

    override suspend fun updateRole(memberId: Long, chatId: Long, role: MemberRole): ResultContainer<MemberChat> =
        safeDbQuery {
            MemberChats.upsert(MemberChats.memberId, MemberChats.chatId) {
                it[MemberChats.memberId] = memberId
                it[MemberChats.chatId] = chatId
                it[MemberChats.role] = role.name
            }
            MemberChats.selectAll()
                .where { memberChatPredicate(memberId, chatId) }
                .singleOrNull()?.toMemberChat()
                ?: throw ResourceNotFoundException("MemberChat", "$memberId/$chatId")
        }

    override suspend fun findAllByChatId(chatId: Long): ResultContainer<List<MemberChat>> =
        safeDbQuery {
            MemberChats.selectAll()
                .where { MemberChats.chatId eq chatId }
                .map { it.toMemberChat() }
        }

    override suspend fun existsByMemberIdAndChatId(memberId: Long, chatId: Long): ResultContainer<Boolean> =
        safeDbQuery {
            MemberChats.selectAll()
                .where { memberChatPredicate(memberId, chatId) }
                .any()
        }

    override suspend fun findChatIdsByMemberId(memberId: Long): ResultContainer<List<Long>> =
        safeDbQuery {
            MemberChats.selectAll()
                .where { MemberChats.memberId eq memberId }
                .map { it[MemberChats.chatId] }
        }

    private fun memberChatPredicate(memberId: Long, chatId: Long) =
        (MemberChats.memberId eq memberId) and (MemberChats.chatId eq chatId)
}
