package com.ua.astrumon.data.db.table

import org.jetbrains.exposed.dao.id.LongIdTable

object Members : LongIdTable("members") {
    val userId = long("user_id").uniqueIndex()
    val username = varchar("username", 64)
    val firstname = varchar("firstname", 128)
}
