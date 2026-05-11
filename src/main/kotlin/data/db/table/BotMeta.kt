package com.ua.astrumon.data.db.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object BotMeta : Table("bot_meta") {
    val key = varchar("key", 64)
    val value = text("value")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(key)
}
