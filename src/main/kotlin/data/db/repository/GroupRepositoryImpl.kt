package com.ua.astrumon.data.db.repository

import com.ua.astrumon.data.db.safeDbQuery
import com.ua.astrumon.common.exception.*
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.data.db.table.Groups
import com.ua.astrumon.domain.model.Group
import com.ua.astrumon.domain.repository.GroupRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory

class GroupRepositoryImpl : GroupRepository {
    private val logger = LoggerFactory.getLogger(GroupRepositoryImpl::class.java)

    override suspend fun getAllGroups(chatId: Long): ResultContainer<List<Group>> = safeDbQuery {
        logger.info("getAllGroups called for chatId=$chatId")
        Groups.selectAll().where { Groups.chatId eq chatId }.map { row ->
            Group(
                id = row[Groups.id].value,
                chatId = row[Groups.chatId],
                name = row[Groups.name],
                memberUsernames = emptyList()
            )
        }
    }

    override suspend fun findGroupByKey(chatId: Long, key: String): ResultContainer<Group> = safeDbQuery {
        logger.info("findGroupByKey called with chatId=$chatId, key='$key'")
        val result = Groups.selectAll()
            .where { (Groups.chatId eq chatId) and (Groups.name eq key) }
            .singleOrNull()
        result?.let { row ->
            Group(
                id = row[Groups.id].value,
                chatId = row[Groups.chatId],
                name = row[Groups.name],
                memberUsernames = emptyList()
            )
        } ?: throw ResourceNotFoundException("Group", key)
    }

    override suspend fun createGroup(chatId: Long, name: String): ResultContainer<Group> = safeDbQuery {
        logger.info("createGroup called with chatId=$chatId, name='$name'")
        val existing = Groups.selectAll()
            .where { (Groups.chatId eq chatId) and (Groups.name eq name) }
            .singleOrNull()
        if (existing != null) {
            throw DuplicateResourceException("Group", name)
        }

        val insertedId = Groups.insert {
            it[Groups.chatId] = chatId
            it[Groups.name] = name
        } get Groups.id

        Group(
            id = insertedId.value,
            chatId = chatId,
            name = name,
            memberUsernames = emptyList()
        )
    }

    override suspend fun deleteGroup(chatId: Long, key: String): ResultContainer<Unit> = safeDbQuery {
        logger.info("deleteGroup called with chatId=$chatId, key='$key'")
        val deletedCount = Groups.deleteWhere {
            (Groups.chatId eq chatId) and (Groups.name eq key)
        }
        if (deletedCount == 0) {
            throw ResourceNotFoundException("Group", key)
        }
    }
}
