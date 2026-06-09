package com.ua.astrumon.data.mapper

import com.ua.astrumon.data.db.table.MemberChats
import com.ua.astrumon.data.db.table.Members
import com.ua.astrumon.domain.model.BirthDate
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.model.MemberWithChat
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toMember() =
    Member(
        id = this[Members.id].value,
        userId = this[Members.userId],
        username = this[Members.username],
        firstName = this[Members.firstname],
        birthday = this[Members.birthMd]?.toInt()?.let { BirthDate.fromMmDd(it) },
    )

fun ResultRow.toMemberWithChat() =
    MemberWithChat(
        id = this[Members.id].value,
        userId = this[Members.userId],
        username = this[Members.username],
        firstName = this[Members.firstname],
        role = MemberRole.valueOf(this[MemberChats.role]),
        joinedAt = this[MemberChats.joinedAt],
        birthday = this[Members.birthMd]?.toInt()?.let { BirthDate.fromMmDd(it) },
    )
