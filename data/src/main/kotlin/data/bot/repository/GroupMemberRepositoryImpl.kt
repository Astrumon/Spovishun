package com.ua.astrumon.data.bot.repository

import com.ua.astrumon.common.exception.BusinessException
import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.data.bot.table.GroupMembers
import com.ua.astrumon.data.bot.table.Groups
import com.ua.astrumon.data.bot.table.Members
import com.ua.astrumon.data.db.eqIgnoreCase
import com.ua.astrumon.data.db.safeDbQuery
import com.ua.astrumon.domain.bot.repository.GroupMemberRepository
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

class GroupMemberRepositoryImpl : GroupMemberRepository {
    override suspend fun addMemberToGroup(
        chatId: Long,
        groupKey: String,
        username: String,
    ): ResultContainer<Unit> = safeDbQuery {
        val group = getGroupByChat(chatId, groupKey) ?: throw ResourceNotFoundException("Group", groupKey)
        val member = getMemberByUsername(username) ?: throw ResourceNotFoundException("Member", username)

        val existing = GroupMembers
            .selectAll()
            .where {
                (GroupMembers.group eq group[Groups.id]) and (GroupMembers.member eq member[Members.id])
            }.singleOrNull()

        if (existing != null) {
            throw DuplicateResourceException("Group Member", "$username in group $groupKey")
        }

        GroupMembers.insert {
            it[GroupMembers.group] = group[Groups.id]
            it[GroupMembers.member] = member[Members.id]
            it[GroupMembers.joinedAt] = Clock.System.now()
        }
    }

    override suspend fun removeMemberFromGroup(
        chatId: Long,
        groupKey: String,
        username: String,
    ): ResultContainer<Unit> = safeDbQuery {
        val group = getGroupByChat(chatId, groupKey) ?: throw ResourceNotFoundException("Group", groupKey)
        val member = getMemberByUsername(username) ?: throw ResourceNotFoundException("Member", username)

        val deletedCount = GroupMembers.deleteWhere {
            (GroupMembers.group eq group[Groups.id]) and (GroupMembers.member eq member[Members.id])
        }

        if (deletedCount == 0) {
            throw BusinessException("Member $username is not in group $groupKey")
        }
    }

    /**
     * One round trip for the whole set — the per-group variant made a chat with N groups cost N
     * queries, each of which also re-selected the `groups` row for an id the caller already had.
     *
     * The join on [Groups] is what makes [chatId] a real guard rather than a decorative parameter:
     * a group id from another chat can never leak into the result. Ordering by the membership row id
     * keeps insertion order, which is what the per-group queries returned.
     *
     * A chat with no groups short-circuits before [safeDbQuery], so it opens no transaction at all.
     */
    override suspend fun getMembersForGroups(
        chatId: Long,
        groupIds: Collection<Long>,
    ): ResultContainer<Map<Long, List<String>>> {
        if (groupIds.isEmpty()) {
            return ResultContainer.success(emptyMap())
        }

        return safeDbQuery {
            GroupMembers
                .innerJoin(Members)
                .innerJoin(Groups)
                .selectAll()
                .where { (Groups.chatId eq chatId) and (GroupMembers.group inList groupIds) }
                .orderBy(GroupMembers.id)
                .groupBy({ row -> row[GroupMembers.group].value }, { row -> row[Members.username] })
        }
    }

    private fun getGroupByChat(
        chatId: Long,
        groupKey: String,
    ) = Groups
        .selectAll()
        .where { (Groups.chatId eq chatId) and (Groups.name eq groupKey) }
        .singleOrNull()

    private fun getMemberByUsername(username: String) = Members.selectAll().where { Members.username eqIgnoreCase username }.singleOrNull()
}
