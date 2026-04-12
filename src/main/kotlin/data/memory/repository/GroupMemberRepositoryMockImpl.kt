package com.ua.astrumon.data.memory.repository

import com.ua.astrumon.common.exception.BusinessException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.repository.GroupMemberRepository
import org.slf4j.LoggerFactory

class GroupMemberRepositoryMockImpl : GroupMemberRepository {
    private val logger = LoggerFactory.getLogger(GroupMemberRepositoryMockImpl::class.java)
    private val groupMembers = mutableMapOf<String, MutableSet<String>>() // key = "$chatId:$groupKey"

    override suspend fun addMemberToGroup(chatId: Long, groupKey: String, username: String): ResultContainer<Unit> {
        logger.info("DEV: Adding member $username to group $groupKey")
        val members = groupMembers.getOrPut("$chatId:$groupKey") { mutableSetOf() }

        if (username in members) {
            logger.info("DEV: Member $username already exists in group $groupKey")
            return ResultContainer.failure(
                BusinessException(
                    "Member already in group",
                    "Member already exists in this group"
                )
            )
        }

        members.add(username)
        logger.info("DEV: Member $username added to group $groupKey successfully")
        return ResultContainer.success(Unit)
    }

    override suspend fun removeMemberFromGroup(
        chatId: Long,
        groupKey: String,
        username: String
    ): ResultContainer<Unit> {
        logger.info("DEV: Removing member $username from group $groupKey")
        val members = groupMembers["$chatId:$groupKey"]

        if (members == null || username !in members) {
            logger.info("DEV: Member $username not found in group $groupKey")
            return ResultContainer.failure(ResourceNotFoundException("Group member", "$username in group $groupKey"))
        }

        members.remove(username)
        logger.info("DEV: Member $username removed from group $groupKey successfully")
        return ResultContainer.success(Unit)
    }

    override suspend fun getGroupMembers(chatId: Long, groupKey: String): ResultContainer<List<String>> =
        ResultContainer.success(groupMembers["$chatId:$groupKey"]?.toList() ?: emptyList())
}
