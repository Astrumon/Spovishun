package com.ua.astrumon.data.memory.repository

import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.repository.MemberRepository
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory

class MemberRepositoryMockImpl : MemberRepository {
    private val logger = LoggerFactory.getLogger(MemberRepositoryMockImpl::class.java)
    private val members = mutableMapOf<String, Member>()
    private var nextId = 1L

    override suspend fun findByUsername(username: String): ResultContainer<Member?> {
        logger.info("DEV: Finding member by username: $username")
        return ResultContainer.success(members[username])
    }

    override suspend fun findByUserId(userId: Long): ResultContainer<Member?> {
        logger.info("DEV: Finding member by userId: $userId")
        return ResultContainer.success(members.values.find { it.userId == userId })
    }

    override suspend fun findByChatIdAndUserId(chatId: Long, userId: Long): ResultContainer<Member?> {
        logger.info("DEV: Finding member by chatId: $chatId and userId: $userId")
        return ResultContainer.success(members.values.find { it.chatId == chatId && it.userId == userId })
    }

    override suspend fun save(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        joinedAt: Instant?,
        role: MemberRole
    ): ResultContainer<Member> {
        logger.info("DEV: Saving member - userId: $userId, username: $username, firstName: $firstName")
        val member = Member(
            id = nextId++,
            userId = userId,
            username = username,
            firstName = firstName,
            joinedAt = joinedAt,
            chatId = chatId,
            role = role
        )
        members[username] = member
        logger.info("DEV: Member saved successfully: $member")
        return ResultContainer.success(member)
    }

    override suspend fun updateRole(chatId: Long, userId: Long, role: MemberRole): ResultContainer<Member> {
        logger.info("DEV: Updating role for userId: $userId to $role")
        val entry = members.entries.find { it.value.chatId == chatId && it.value.userId == userId }
            ?: return ResultContainer.failure(ResourceNotFoundException("Member", userId.toString()))
        val updated = entry.value.copy(role = role)
        members[entry.key] = updated
        return ResultContainer.success(updated)
    }

    override suspend fun findAll(): ResultContainer<List<Member>> {
        logger.info("DEV: Finding all members. Total count: ${members.size}")
        return ResultContainer.success(members.values.toList())
    }
}
