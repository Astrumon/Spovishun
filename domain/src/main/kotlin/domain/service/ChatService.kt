package com.ua.astrumon.domain.service

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Chat
import com.ua.astrumon.domain.repository.ChatRepository

class ChatService(
    private val chatRepository: ChatRepository,
) {
    suspend fun ensureChat(
        chatId: Long,
        title: String?,
        type: String?,
    ): ResultContainer<Chat> = chatRepository.findById(chatId).flatMap { existing ->
        if (existing != null) {
            ResultContainer.success(existing)
        } else {
            chatRepository.save(chatId, title, type)
        }
    }

    suspend fun getAllChatIds(): ResultContainer<List<Long>> = chatRepository.findAllChatIds()

    suspend fun setAnnouncementsEnabled(
        chatId: Long,
        enabled: Boolean,
    ): ResultContainer<Unit> = chatRepository.setAnnouncementsEnabled(chatId, enabled)

    suspend fun getAnnouncementChatIds(): ResultContainer<List<Long>> = chatRepository.findAnnouncementChatIds()
}
