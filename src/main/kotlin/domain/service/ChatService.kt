package com.ua.astrumon.domain.service

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Chat
import com.ua.astrumon.domain.repository.ChatRepository

class ChatService(
    private val chatRepository: ChatRepository
) {

    suspend fun ensureChat(chatId: Long, title: String?, type: String?): ResultContainer<Chat> {
        return chatRepository.findById(chatId)
            .flatMap { existing ->
                if (existing != null) {
                    ResultContainer.success(existing)
                } else {
                    chatRepository.save(chatId, title, type)
                }
            }
    }
}
