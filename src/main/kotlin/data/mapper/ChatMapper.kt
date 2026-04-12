package com.ua.astrumon.data.mapper

import com.ua.astrumon.data.db.table.Chats
import com.ua.astrumon.domain.model.Chat
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toChat() = Chat(
    chatId = this[Chats.chatId],
    title = this[Chats.title],
    type = this[Chats.type],
    registeredAt = this[Chats.registeredAt]
)
