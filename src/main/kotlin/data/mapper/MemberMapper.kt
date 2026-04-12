package com.ua.astrumon.data.mapper

import com.ua.astrumon.data.db.table.Members
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.model.MemberRole
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toMember() = Member(
    id = this[Members.id].value,
    chatId = this[Members.chatId],
    userId = this[Members.userId],
    username = this[Members.username],
    firstName = this[Members.firstname],
    joinedAt = this[Members.joinedAt],
    role = MemberRole.valueOf(this[Members.role])
)