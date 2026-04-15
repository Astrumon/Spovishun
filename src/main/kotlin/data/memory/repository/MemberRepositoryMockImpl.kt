package com.ua.astrumon.data.memory.repository

import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.model.MemberWithChat
import com.ua.astrumon.domain.repository.MemberChatRepository
import com.ua.astrumon.domain.repository.MemberRepository
import org.slf4j.LoggerFactory

class MemberRepositoryMockImpl(
    private val memberChatRepository: MemberChatRepository,
) : MemberRepository {
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

    override suspend fun findMemberWithChatByChatIdAndUsername(chatId: Long, username: String): ResultContainer<MemberWithChat?> {
        logger.info("DEV: Finding memberWithChat chatId=$chatId username=$username")
        val member = members.values.find { it.username == username }
            ?: return ResultContainer.success(null)
        return memberChatRepository.findByMemberIdAndChatId(member.id, chatId).map { memberChat ->
            memberChat?.let {
                MemberWithChat(
                    id = member.id,
                    userId = member.userId,
                    username = member.username,
                    firstName = member.firstName,
                    role = it.role,
                    joinedAt = it.joinedAt,
                )
            }
        }
    }

    override suspend fun findAllMembersWithChatByChatId(chatId: Long): ResultContainer<List<MemberWithChat>> {
        logger.info("DEV: Finding all membersWithChat for chatId=$chatId")
        return memberChatRepository.findAllByChatId(chatId).map { memberChats ->
            memberChats.mapNotNull { memberChat ->
                val member = members.values.find { it.id == memberChat.memberId }
                member?.let {
                    MemberWithChat(
                        id = it.id,
                        userId = it.userId,
                        username = it.username,
                        firstName = it.firstName,
                        role = memberChat.role,
                        joinedAt = memberChat.joinedAt,
                    )
                }
            }
        }
    }
}
