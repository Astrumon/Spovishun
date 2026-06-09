package com.ua.astrumon.data.mapper

import com.ua.astrumon.data.db.table.MemberChats
import com.ua.astrumon.domain.model.MemberChat
import com.ua.astrumon.domain.model.MemberRole
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toMemberChat() = MemberChat(
    memberId = this[MemberChats.memberId],
    chatId = this[MemberChats.chatId],
    role = MemberRole.valueOf(this[MemberChats.role]),
    joinedAt = this[MemberChats.joinedAt],
)
