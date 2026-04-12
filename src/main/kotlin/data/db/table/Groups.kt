package com.ua.astrumon.data.db.table

import org.jetbrains.exposed.dao.id.LongIdTable

object Groups: LongIdTable("groups") {
    val chatId = reference("chat_id", Chats.chatId)
    val name = varchar("name", 64)

    init {
        uniqueIndex(chatId, name)
    }
}