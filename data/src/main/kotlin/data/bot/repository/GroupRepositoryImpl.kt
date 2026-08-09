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
import com.ua.astrumon.domain.bot.model.GroupSettingsPatch
import com.ua.astrumon.domain.bot.model.Patch
import com.ua.astrumon.domain.bot.model.PingMark
import com.ua.astrumon.domain.bot.repository.GroupRepository
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.UpsertStatement
import org.jetbrains.exposed.sql.update
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
        settings: GroupSettingsPatch,
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

        // The row is defaulted first and then patched through the one writer, rather than having the
        // insert carry the values: that keeps `onUpdateExclude` the single description of which
        // columns a write owns, instead of splitting it across a create path and an edit path.
        // `patch.name` is deliberately ignored — the name is already this insert's own argument.
        writeSettings(insertedId, settings.settingWrites())

        // The returned group carries what was just written, not the model's defaults: a method that
        // accepts settings and hands back a group claiming to have none is a trap for the first
        // caller who reads the result instead of re-querying.
        Group(
            id = insertedId.value,
            chatId = chatId,
            name = name,
            memberUsernames = emptyList(),
            icon = (settings.icon as? Patch.Value)?.value,
            pingMark = (settings.pingMark as? Patch.Value)?.value ?: PingMark.Default,
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
        writeSettings(requireGroupId(chatId, key), listOf(SettingWrite(GroupSettings.readinessEnabled, enabled)))
    }

    /**
     * The group is resolved **once**, by the name the caller passed, before anything is written. That
     * is what makes parameter order irrelevant: a rename in the same patch would otherwise invalidate
     * the very key a settings write needs to look itself up with (spovishun-180).
     */
    override suspend fun updateGroup(
        chatId: Long,
        key: String,
        patch: GroupSettingsPatch,
    ): ResultContainer<Unit> = safeDbQuery {
        val groupId = requireGroupId(chatId, key)

        (patch.name as? Patch.Value)?.let { rename(chatId, groupId, it.value) }
        writeSettings(groupId, patch.settingWrites())
    }

    /** Renaming onto the group's own name is a no-op, not a conflict — the row it finds is itself. */
    private fun rename(
        chatId: Long,
        groupId: EntityID<Long>,
        newName: String,
    ) {
        val taken = Groups.rowFor(chatId, newName)?.get(Groups.id)
        if (taken != null && taken != groupId) {
            throw DuplicateResourceException("Group", newName)
        }
        Groups.update({ Groups.id eq groupId }) { it[name] = newName }
    }

    /**
     * The only writer of [GroupSettings]. Its `onUpdateExclude` is derived, never listed: every
     * settings column except the ones this write owns. Without the exclusion the upsert's DO UPDATE
     * would also write the defaults of columns it never meant to touch — clearing a group's icon
     * while toggling readiness, say. Deriving it means a column added to [GroupSettings] joins the
     * exclusion set on its own, instead of waiting to be forgotten in a hand-written list.
     */
    private fun writeSettings(
        groupId: EntityID<Long>,
        writes: List<SettingWrite<*>>,
    ) {
        if (writes.isEmpty()) return

        val written = writes.map { it.column }.toSet()
        GroupSettings.upsert(
            GroupSettings.groupId,
            onUpdateExclude = GroupSettings.columns - written - GroupSettings.groupId,
        ) { statement ->
            statement[GroupSettings.groupId] = groupId
            writes.forEach { it.applyTo(statement) }
        }
    }

    /** Settings are keyed by group id, so every settings write resolves the group by name first. */
    private fun requireGroupId(
        chatId: Long,
        key: String,
    ): EntityID<Long> = Groups.rowFor(chatId, key)?.get(Groups.id) ?: throw ResourceNotFoundException("Group", key)
}

/**
 * One column and the value bound to it, kept together so a heterogeneous list of writes stays typed:
 * the element's own type parameter is what makes `statement[column] = value` check out.
 */
private class SettingWrite<T>(
    val column: Column<T>,
    private val value: T,
) {
    fun applyTo(statement: UpsertStatement<Long>) {
        statement[column] = value
    }
}

private fun GroupSettingsPatch.settingWrites(): List<SettingWrite<*>> = buildList {
    (icon as? Patch.Value)?.let { add(SettingWrite(GroupSettings.icon, it.value)) }
    (pingMark as? Patch.Value)?.let { addAll(it.value.settingWrites()) }
}

/**
 * [PingMark.Hidden] writes the flag and nothing else — leaving `ping_mark` out of the write is what
 * puts it in `onUpdateExclude`, and therefore what makes hiding a mark preserve the emoji behind it.
 */
private fun PingMark.settingWrites(): List<SettingWrite<*>> = when (this) {
    PingMark.Hidden -> listOf(SettingWrite(GroupSettings.pingMarkEnabled, false))
    PingMark.Default -> listOf(
        SettingWrite(GroupSettings.pingMark, null),
        SettingWrite(GroupSettings.pingMarkEnabled, true),
    )
    is PingMark.Custom -> listOf(
        SettingWrite(GroupSettings.pingMark, emoji),
        SettingWrite(GroupSettings.pingMarkEnabled, true),
    )
}
