package com.ua.astrumon.data.mapper

import com.ua.astrumon.data.db.table.Groups
import com.ua.astrumon.domain.model.Group
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toGroup() =
    Group(
        id = this[Groups.id].value,
        chatId = this[Groups.chatId],
        name = this[Groups.name],
        memberUsernames = emptyList(),
    )
