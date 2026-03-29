package com.ua.astrumon.data.memory.repository

import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Group
import com.ua.astrumon.domain.repository.GroupRepository
import org.slf4j.LoggerFactory

class GroupRepositoryMockImpl : GroupRepository {
    private val logger = LoggerFactory.getLogger(GroupRepositoryMockImpl::class.java)
    private val groups = mutableMapOf<String, Group>()
    private var nextId = 1L

    override suspend fun getAllGroups(chatId: Long): ResultContainer<List<Group>> {
        return ResultContainer.success(groups.values.filter { it.chatId == chatId })
    }

    override suspend fun findGroupByKey(chatId: Long, key: String): ResultContainer<Group> {
        logger.info("DEV: Finding group by key: $key")
        return groups["$chatId:$key"]?.let { ResultContainer.success(it) }
            ?: ResultContainer.failure(ResourceNotFoundException("Group", key))
    }

    override suspend fun createGroup(chatId: Long, name: String): ResultContainer<Group> {
        logger.info("DEV: Creating group - name: $name")

        if (groups.containsKey(name)) {
            return ResultContainer.failure(DuplicateResourceException("Group", name))
        }

        val group = Group(id = nextId++, chatId = chatId, name = name, memberUsernames = emptyList())
        groups["$chatId:$name"] = group
        logger.info("DEV: Group created successfully: $group")
        return ResultContainer.success(group)
    }

    override suspend fun deleteGroup(chatId: Long, key: String): ResultContainer<Unit> {
        logger.info("DEV: Deleting group: $key")
        return if (groups.remove("$chatId:$key") != null) {
            logger.info("DEV: Group deleted successfully: $key")
            ResultContainer.success(Unit)
        } else {
            ResultContainer.failure(ResourceNotFoundException("Group", key))
        }
    }
}
