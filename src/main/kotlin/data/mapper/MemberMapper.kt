package com.ua.astrumon.data.mapper

import com.ua.astrumon.data.db.table.Members
import com.ua.astrumon.domain.model.Member
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toMember() = Member(
    id = this[Members.id].value,
    userId = this[Members.userId],
    username = this[Members.username],
    firstName = this[Members.firstname],
)
