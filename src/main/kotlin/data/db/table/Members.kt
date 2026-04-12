package com.ua.astrumon.data.db.table

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object Members : LongIdTable("members") {
    val chatId = long("chat_id")
    val userId = long("user_id").index()
    val username = varchar("username", 64)
    val firstname = varchar("firstname", 128)
    val joinedAt = timestamp("joined_at").nullable()
    val role = varchar("role", 16).default("MEMBER")

    init {
        uniqueIndex(chatId, userId)    // один юзер — один раз у чаті
        uniqueIndex(chatId, username)  // один username — один раз у чаті
    }
}