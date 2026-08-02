package com.ua.astrumon.domain.bot.service

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.bot.model.BotLanguage
import com.ua.astrumon.domain.bot.model.Chat
import com.ua.astrumon.domain.bot.repository.ChatRepository

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

    suspend fun getAnnouncementChats(): ResultContainer<List<Chat>> = chatRepository.findAnnouncementChats()

    suspend fun setReadinessEnabled(
        chatId: Long,
        enabled: Boolean,
    ): ResultContainer<Unit> = chatRepository.setReadinessEnabled(chatId, enabled)

    /** Readiness is the default for a chat the bot has never persisted a row for yet. */
    suspend fun isReadinessEnabled(chatId: Long): ResultContainer<Boolean> =
        chatRepository.findById(chatId).map { it?.readinessEnabled ?: true }

    suspend fun setLanguage(
        chatId: Long,
        language: BotLanguage,
    ): ResultContainer<Unit> = chatRepository.setLanguage(chatId, language)

    /** Ukrainian is the default both for chats registered before the column existed and for unknown chats. */
    suspend fun getLanguage(chatId: Long): ResultContainer<BotLanguage> =
        chatRepository.findById(chatId).map { it?.language ?: BotLanguage.UK }
}
