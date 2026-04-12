package com.ua.astrumon.data.db.table

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object GroupMembers: LongIdTable("group_members") {
    val group = reference("group_id", Groups)
    val member = reference("member_id", Members)
    val joinedAt = timestamp("joined_at")
}