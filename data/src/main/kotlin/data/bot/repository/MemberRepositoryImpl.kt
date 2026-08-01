package com.ua.astrumon.data.bot.repository

import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.data.bot.mapper.toMember
import com.ua.astrumon.data.bot.mapper.toMemberWithChat
import com.ua.astrumon.data.bot.table.MemberChats
import com.ua.astrumon.data.bot.table.Members
import com.ua.astrumon.data.db.eqIgnoreCase
import com.ua.astrumon.data.db.inListIgnoreCase
import com.ua.astrumon.data.db.safeDbQuery
import com.ua.astrumon.domain.bot.model.BirthDate
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.domain.bot.model.MemberWithChat
import com.ua.astrumon.domain.bot.repository.MemberRepository
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert

class MemberRepositoryImpl : MemberRepository {
    override suspend fun findById(id: Long): ResultContainer<Member?> = safeDbQuery {
        Members
            .selectAll()
            .where { Members.id eq id }
            .singleOrNull()
            ?.toMember()
    }

    override suspend fun findByUserId(userId: Long): ResultContainer<Member?> = safeDbQuery {
        Members
            .selectAll()
            .where { Members.userId eq userId }
            .singleOrNull()
            ?.toMember()
    }

    override suspend fun findByUsername(username: String): ResultContainer<Member?> = safeDbQuery {
        Members
            .selectAll()
            .where { Members.username eqIgnoreCase username }
            .singleOrNull()
            ?.toMember()
    }

    override suspend fun findAllByUsernames(usernames: Collection<String>): ResultContainer<List<Member>> = safeDbQuery {
        if (usernames.isEmpty()) {
            return@safeDbQuery emptyList()
        }
        Members
            .selectAll()
            .where { Members.username inListIgnoreCase usernames }
            .map { it.toMember() }
    }

    override suspend fun saveOrUpdate(
        userId: Long,
        username: String,
        firstName: String,
    ): ResultContainer<Member> = safeDbQuery {
        Members.upsert(Members.userId) {
            it[Members.userId] = userId
            it[Members.username] = username
            it[Members.firstname] = firstName
        }
        Members
            .selectAll()
            .where { Members.userId eq userId }
            .singleOrNull()
            ?.toMember()
            ?: throw ResourceNotFoundException("Member", userId.toString())
    }

    override suspend fun findMemberWithChatByChatIdAndUsername(
        chatId: Long,
        username: String,
    ): ResultContainer<MemberWithChat?> = safeDbQuery {
        Members
            .innerJoin(MemberChats)
            .selectAll()
            .where { (Members.username eqIgnoreCase username) and (MemberChats.chatId eq chatId) }
            .singleOrNull()
            ?.toMemberWithChat()
    }

    override suspend fun findAllMembersWithChatByChatId(chatId: Long): ResultContainer<List<MemberWithChat>> = safeDbQuery {
        Members
            .innerJoin(MemberChats)
            .selectAll()
            .where { MemberChats.chatId eq chatId }
            .map { it.toMemberWithChat() }
    }

    override suspend fun findAllByBirthMd(birthMd: Int): ResultContainer<List<Member>> = safeDbQuery {
        Members
            .selectAll()
            .where { Members.birthMd eq birthMd.toShort() }
            .map { it.toMember() }
    }

    override suspend fun updateBirthday(
        userId: Long,
        birthday: BirthDate?,
    ): ResultContainer<Member> = safeDbQuery {
        Members.update({ Members.userId eq userId }) {
            it[Members.birthMd] = birthday?.toMmDd()?.toShort()
        }
        Members
            .selectAll()
            .where { Members.userId eq userId }
            .singleOrNull()
            ?.toMember()
            ?: throw ResourceNotFoundException("Member", userId.toString())
    }
}
