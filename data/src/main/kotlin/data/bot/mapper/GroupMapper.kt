package com.ua.astrumon.data.bot.mapper

import com.ua.astrumon.data.bot.table.Groups
import com.ua.astrumon.domain.bot.model.Group
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toGroup() = Group(
    id = this[Groups.id].value,
    chatId = this[Groups.chatId],
    name = this[Groups.name],
    memberUsernames = emptyList(),
    readinessEnabled = this[Groups.readinessEnabled],
)
