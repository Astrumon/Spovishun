package com.ua.astrumon.data.bot.table

import org.jetbrains.exposed.dao.id.LongIdTable

object Groups : LongIdTable("groups") {
    val chatId = reference("chat_id", Chats.chatId)
    val name = varchar("name", 64)
    val readinessEnabled = bool("readiness_enabled").default(true)

    init {
        uniqueIndex(chatId, name)
    }
}
