package com.ua.astrumon.data.db.repository

import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.data.db.safeDbQuery
import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.data.db.table.Chats
import com.ua.astrumon.data.mapper.toChat
import com.ua.astrumon.domain.model.Chat
import com.ua.astrumon.domain.repository.ChatRepository
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll

class ChatRepositoryImpl : ChatRepository {
    override suspend fun findById(chatId: Long): ResultContainer<Chat?> =
        safeDbQuery {
            Chats.selectAll()
                .where { Chats.chatId eq chatId }
                .singleOrNull()?.toChat()
        }

    override suspend fun save(chatId: Long, title: String?, type: String?): ResultContainer<Chat> =
        safeDbQuery {
            val existing = Chats.selectAll()
                .where { Chats.chatId eq chatId }
                .singleOrNull()?.toChat()

            if (existing != null) return@safeDbQuery existing

            Chats.insertIgnore {
                it[this@insertIgnore.chatId] = chatId
                it[this@insertIgnore.title] = title
                it[this@insertIgnore.type] = type
                it[this@insertIgnore.registeredAt] = Clock.System.now()
            }

            Chats.selectAll()
                .where { Chats.chatId eq chatId }
                .singleOrNull()?.toChat()
                ?: throw ResourceNotFoundException("Chat", chatId.toString())
        }
}
