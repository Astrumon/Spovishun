package com.ua.astrumon.data.memory.repository

import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.MemberChat
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.repository.MemberChatRepository
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory

class MemberChatRepositoryMockImpl : MemberChatRepository {
    private val logger = LoggerFactory.getLogger(MemberChatRepositoryMockImpl::class.java)
    // key: Pair(memberId, chatId)
    private val entries = mutableMapOf<Pair<Long, Long>, MemberChat>()

    override suspend fun findByMemberIdAndChatId(memberId: Long, chatId: Long): ResultContainer<MemberChat?> {
        logger.info("DEV: Finding memberChat memberId=$memberId chatId=$chatId")
        return ResultContainer.success(entries[memberId to chatId])
    }

    override suspend fun save(memberId: Long, chatId: Long, role: MemberRole, joinedAt: Instant?): ResultContainer<MemberChat> {
        logger.info("DEV: Saving memberChat memberId=$memberId chatId=$chatId role=$role")
        val memberChat = MemberChat(memberId = memberId, chatId = chatId, role = role, joinedAt = joinedAt)
        entries[memberId to chatId] = memberChat
        return ResultContainer.success(memberChat)
    }

    override suspend fun updateRole(memberId: Long, chatId: Long, role: MemberRole): ResultContainer<MemberChat> {
        logger.info("DEV: Updating role for memberId=$memberId chatId=$chatId to $role")
        val existing = entries[memberId to chatId]
            ?: return ResultContainer.failure(ResourceNotFoundException("MemberChat", "$memberId/$chatId"))
        val updated = existing.copy(role = role)
        entries[memberId to chatId] = updated
        return ResultContainer.success(updated)
    }

    override suspend fun findAllByChatId(chatId: Long): ResultContainer<List<MemberChat>> {
        logger.info("DEV: Finding all memberChats for chatId=$chatId")
        return ResultContainer.success(entries.values.filter { it.chatId == chatId })
    }

    override suspend fun existsByMemberIdAndChatId(memberId: Long, chatId: Long): ResultContainer<Boolean> =
        ResultContainer.success(entries.containsKey(memberId to chatId))
}
