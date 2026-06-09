package com.ua.astrumon.data.db.repository

import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.data.db.safeDbQuery
import com.ua.astrumon.data.db.table.Groups
import com.ua.astrumon.data.mapper.toGroup
import com.ua.astrumon.domain.model.Group
import com.ua.astrumon.domain.repository.GroupRepository
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

class GroupRepositoryImpl : GroupRepository {
    override suspend fun getAllGroups(chatId: Long): ResultContainer<List<Group>> =
        safeDbQuery {
            Groups.selectAll().where { Groups.chatId eq chatId }.map { it.toGroup() }
        }

    override suspend fun findGroupByKey(
        chatId: Long,
        key: String,
    ): ResultContainer<Group> =
        safeDbQuery {
            Groups
                .selectAll()
                .where { (Groups.chatId eq chatId) and (Groups.name eq key) }
                .singleOrNull()
                ?.toGroup()
                ?: throw ResourceNotFoundException("Group", key)
        }

    override suspend fun createGroup(
        chatId: Long,
        name: String,
    ): ResultContainer<Group> =
        safeDbQuery {
            val existing = Groups
                .selectAll()
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
                memberUsernames = emptyList(),
            )
        }

    override suspend fun deleteGroup(
        chatId: Long,
        key: String,
    ): ResultContainer<Unit> =
        safeDbQuery {
            val deletedCount = Groups.deleteWhere {
                (Groups.chatId eq chatId) and (Groups.name eq key)
            }
            if (deletedCount == 0) {
                throw ResourceNotFoundException("Group", key)
            }
        }
}
