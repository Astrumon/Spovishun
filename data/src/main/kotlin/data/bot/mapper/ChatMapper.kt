package com.ua.astrumon.data.bot.mapper

import com.ua.astrumon.data.bot.table.Chats
import com.ua.astrumon.domain.bot.model.BotLanguage
import com.ua.astrumon.domain.bot.model.Chat
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toChat() = Chat(
    chatId = this[Chats.chatId],
    title = this[Chats.title],
    type = this[Chats.type],
    registeredAt = this[Chats.registeredAt],
    announcementsEnabled = this[Chats.announcementsEnabled],
    readinessEnabled = this[Chats.readinessEnabled],
    language = BotLanguage.fromCode(this[Chats.language]),
)
