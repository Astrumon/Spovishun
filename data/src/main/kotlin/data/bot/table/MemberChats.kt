package com.ua.astrumon.data.bot.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object MemberChats : Table("member_chats") {
    val memberId = long("member_id").references(Members.id)
    val chatId = long("chat_id").references(Chats.chatId)
    val role = varchar("role", 16).default("MEMBER")
    val joinedAt = timestamp("joined_at").nullable()

    override val primaryKey = PrimaryKey(memberId, chatId)
}
