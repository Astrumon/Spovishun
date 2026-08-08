package com.ua.astrumon.data.bot.repository

import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.data.bot.mapper.toGroup
import com.ua.astrumon.data.bot.table.GroupSettings
import com.ua.astrumon.data.bot.table.Groups
import com.ua.astrumon.data.bot.table.rowFor
import com.ua.astrumon.data.db.safeDbQuery
import com.ua.astrumon.domain.bot.model.Group
import com.ua.astrumon.domain.bot.repository.GroupRepository
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
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
        // Selects from the join rather than through Groups.rowFor: the settings columns are part of
        // the answer here, and re-reading them would cost a second query.
        groupsWithSettings
            .selectAll()
            .where(Groups.keyed(chatId, key))
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
        if (Groups.rowFor(chatId, name) != null) {
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
        val deletedCount = Groups.deleteWhere { Groups.keyed(chatId, key) }
        if (deletedCount == 0) {
            throw ResourceNotFoundException("Group", key)
        }
    }

    override suspend fun setReadinessEnabled(
        chatId: Long,
        key: String,
        enabled: Boolean,
    ): ResultContainer<Unit> = safeDbQuery {
        updateSetting(chatId, key, GroupSettings.readinessEnabled, enabled)
    }

    override suspend fun setIcon(
        chatId: Long,
        key: String,
        icon: String?,
    ): ResultContainer<Unit> = safeDbQuery {
        updateSetting(chatId, key, GroupSettings.icon, icon)
    }

    /**
     * The only writer of [GroupSettings]. Its `onUpdateExclude` is derived, never listed: every
     * settings column except the one this write owns. Without the exclusion the upsert's DO UPDATE
     * would also write the defaults of columns it never meant to touch — clearing a group's icon
     * while toggling readiness, say. Deriving it means a column added to [GroupSettings] joins the
     * exclusion set on its own, instead of waiting to be forgotten in a hand-written list.
     */
    private fun <T> updateSetting(
        chatId: Long,
        key: String,
        column: Column<T>,
        value: T,
    ) {
        val groupId = requireGroupId(chatId, key)
        GroupSettings.upsert(
            GroupSettings.groupId,
            onUpdateExclude = GroupSettings.columns - column - GroupSettings.groupId,
        ) {
            it[GroupSettings.groupId] = groupId
            it[column] = value
        }
    }

    /** Settings are keyed by group id, so every settings write resolves the group by name first. */
    private fun requireGroupId(
        chatId: Long,
        key: String,
    ): EntityID<Long> = Groups.rowFor(chatId, key)?.get(Groups.id) ?: throw ResourceNotFoundException("Group", key)
}
