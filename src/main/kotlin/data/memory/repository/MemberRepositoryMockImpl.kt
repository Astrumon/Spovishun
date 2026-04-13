package com.ua.astrumon.data.memory.repository

import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.repository.MemberRepository
import org.slf4j.LoggerFactory

class MemberRepositoryMockImpl : MemberRepository {
    private val logger = LoggerFactory.getLogger(MemberRepositoryMockImpl::class.java)
    private val members = mutableMapOf<Long, Member>()  // keyed by userId
    private var nextId = 1L

    override suspend fun findById(id: Long): ResultContainer<Member?> {
        logger.info("DEV: Finding member by id: $id")
        return ResultContainer.success(members.values.find { it.id == id })
    }

    override suspend fun findByUserId(userId: Long): ResultContainer<Member?> {
        logger.info("DEV: Finding member by userId: $userId")
        return ResultContainer.success(members[userId])
    }

    override suspend fun findByUsername(username: String): ResultContainer<Member?> {
        logger.info("DEV: Finding member by username: $username")
        return ResultContainer.success(members.values.find { it.username == username })
    }

    override suspend fun saveOrUpdate(userId: Long, username: String, firstName: String): ResultContainer<Member> {
        logger.info("DEV: Saving/updating member - userId: $userId, username: $username")
        val existing = members[userId]
        val member = if (existing != null) {
            existing.copy(username = username, firstName = firstName)
        } else {
            Member(id = nextId++, userId = userId, username = username, firstName = firstName)
        }
        members[userId] = member
        return ResultContainer.success(member)
    }
}
