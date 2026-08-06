package com.ua.astrumon.data.bot.repository

import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.data.bot.mapper.toGroup
import com.ua.astrumon.data.bot.table.GroupSettings
import com.ua.astrumon.data.bot.table.Groups
import com.ua.astrumon.data.db.safeDbQuery
import com.ua.astrumon.domain.bot.model.Group
import com.ua.astrumon.domain.bot.repository.GroupRepository
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

class GroupRepositoryImpl : GroupRepository {
    /**
     * Group parameters live in their own 1:1 table (spovishun-32). The join is a LEFT one so a group
     * whose settings row is missing still reads back — [toGroup] supplies the table's own defaults.
     */
    private val groupsWithSettings = Groups.leftJoin(GroupSettings, { Groups.id }, { GroupSettings.groupId })

    override suspend fun getAllGroups(chatId: Long): ResultContainer<List<Group>> = safeDbQuery {
        groupsWithSettings.selectAll().where { Groups.chatId eq chatId }.map { it.toGroup() }
    }

    override suspend fun findGroupByKey(
        chatId: Long,
        key: String,
    ): ResultContainer<Group> = safeDbQuery {
        groupsWithSettings
            .selectAll()
            .where { (Groups.chatId eq chatId) and (Groups.name eq key) }
            .singleOrNull()
            ?.toGroup()
            ?: throw ResourceNotFoundException("Group", key)
    }

    override suspend fun findGroupById(
        chatId: Long,
        groupId: Long,
    ): ResultContainer<Group> = safeDbQuery {
        groupsWithSettings
            .selectAll()
            .where { (Groups.chatId eq chatId) and (Groups.id eq groupId) }
            .singleOrNull()
            ?.toGroup()
            ?: throw ResourceNotFoundException("Group", groupId.toString())
    }

    override suspend fun createGroup(
        chatId: Long,
        name: String,
    ): ResultContainer<Group> = safeDbQuery {
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

        // Same suspended transaction as the group insert, so a group is never left without settings.
        GroupSettings.insert {
            it[groupId] = insertedId
        }

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
    ): ResultContainer<Unit> = safeDbQuery {
        val deletedCount = Groups.deleteWhere {
            (Groups.chatId eq chatId) and (Groups.name eq key)
        }
        if (deletedCount == 0) {
            throw ResourceNotFoundException("Group", key)
        }
    }

    override suspend fun setReadinessEnabled(
        chatId: Long,
        key: String,
        enabled: Boolean,
    ): ResultContainer<Unit> = safeDbQuery {
        val groupId = requireGroupId(chatId, key)
        // onUpdateExclude keeps this write off the icon column: without it the upsert's DO UPDATE
        // would also write the icon default it never meant to touch, clearing a group's icon.
        GroupSettings.upsert(GroupSettings.groupId, onUpdateExclude = listOf(GroupSettings.icon)) {
            it[GroupSettings.groupId] = groupId
            it[readinessEnabled] = enabled
        }
        Unit
    }

    override suspend fun setIcon(
        chatId: Long,
        key: String,
        icon: String?,
    ): ResultContainer<Unit> = safeDbQuery {
        val groupId = requireGroupId(chatId, key)
        // Mirror of setReadinessEnabled: exclude the column this write does not own, or setting an
        // icon would silently re-enable readiness on a group that opted out.
        GroupSettings.upsert(GroupSettings.groupId, onUpdateExclude = listOf(GroupSettings.readinessEnabled)) {
            it[GroupSettings.groupId] = groupId
            it[GroupSettings.icon] = icon
        }
        Unit
    }

    /** Settings are keyed by group id, so every settings write resolves the group by name first. */
    private fun requireGroupId(
        chatId: Long,
        key: String,
    ): EntityID<Long> = Groups
        .selectAll()
        .where { (Groups.chatId eq chatId) and (Groups.name eq key) }
        .singleOrNull()
        ?.get(Groups.id)
        ?: throw ResourceNotFoundException("Group", key)
}
