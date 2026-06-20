package com.ua.astrumon.domain.repository

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Chat

interface ChatRepository {
    suspend fun findById(chatId: Long): ResultContainer<Chat?>

    suspend fun save(
        chatId: Long,
        title: String?,
        type: String?,
    ): ResultContainer<Chat>

    suspend fun findAllChatIds(): ResultContainer<List<Long>>

    suspend fun setAnnouncementsEnabled(
        chatId: Long,
        enabled: Boolean,
    ): ResultContainer<Unit>

    suspend fun findAnnouncementChatIds(): ResultContainer<List<Long>>
}
