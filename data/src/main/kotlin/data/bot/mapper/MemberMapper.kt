package com.ua.astrumon.data.bot.mapper

import com.ua.astrumon.data.bot.table.MemberChats
import com.ua.astrumon.data.bot.table.Members
import com.ua.astrumon.domain.bot.model.BirthDate
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.model.MemberWithChat
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toMember() = Member(
    id = this[Members.id].value,
    userId = this[Members.userId],
    username = this[Members.username],
    firstName = this[Members.firstname],
    birthday = this[Members.birthMd]?.toInt()?.let { BirthDate.fromMmDd(it) },
)

fun ResultRow.toMemberWithChat() = MemberWithChat(
    id = this[Members.id].value,
    userId = this[Members.userId],
    username = this[Members.username],
    firstName = this[Members.firstname],
    role = MemberRole.valueOf(this[MemberChats.role]),
    joinedAt = this[MemberChats.joinedAt],
    birthday = this[Members.birthMd]?.toInt()?.let { BirthDate.fromMmDd(it) },
)
