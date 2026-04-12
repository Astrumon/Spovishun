package com.ua.astrumon.data.db.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object Chats : Table("chats") {
    val chatId = long("chat_id")
    val title = varchar("title", 255).nullable()
    val type = varchar("type", 32).nullable()
    val registeredAt = timestamp("registered_at")

    override val primaryKey = PrimaryKey(chatId)
}
