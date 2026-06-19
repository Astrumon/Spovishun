package com.ua.astrumon.data.db.repository

import com.ua.astrumon.common.exception.BusinessException
import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.data.db.eqIgnoreCase
import com.ua.astrumon.data.db.safeDbQuery
import com.ua.astrumon.data.db.table.GroupMembers
import com.ua.astrumon.data.db.table.Groups
import com.ua.astrumon.data.db.table.Members
import com.ua.astrumon.domain.repository.GroupMemberRepository
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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

    override suspend fun getGroupMembers(
        chatId: Long,
        groupKey: String,
    ): ResultContainer<List<String>> = safeDbQuery {
        val group = Groups
            .selectAll()
            .where { (Groups.chatId eq chatId) and (Groups.name eq groupKey) }
            .singleOrNull()
            ?: throw ResourceNotFoundException("Group", groupKey)

        GroupMembers
            .innerJoin(Members)
            .selectAll()
            .where { GroupMembers.group eq group[Groups.id] }
            .map { row -> row[Members.username] }
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
