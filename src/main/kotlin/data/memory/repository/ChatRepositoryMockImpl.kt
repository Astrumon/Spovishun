package com.ua.astrumon.data.memory.repository

import com.ua.astrumon.common.result.ResultContainer
import com.ua.astrumon.domain.model.Chat
import com.ua.astrumon.domain.repository.ChatRepository
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

class ChatRepositoryMockImpl : ChatRepository {
    private val logger = LoggerFactory.getLogger(ChatRepositoryMockImpl::class.java)
    private val chats = mutableMapOf<Long, Chat>()

    override suspend fun findById(chatId: Long): ResultContainer<Chat?> {
        logger.info("DEV: Finding chat by chatId: $chatId")
        return ResultContainer.success(chats[chatId])
    }

    override suspend fun save(chatId: Long, title: String?, type: String?): ResultContainer<Chat> {
        logger.info("DEV: Saving chat - chatId: $chatId, title: $title, type: $type")
        val existing = chats[chatId]
        if (existing != null) {
            logger.info("DEV: Chat already exists: $existing")
            return ResultContainer.success(existing)
        }
        val chat = Chat(
            chatId = chatId,
            title = title,
            type = type,
            registeredAt = Clock.System.now()
        )
        chats[chatId] = chat
        logger.info("DEV: Chat saved successfully: $chat")
        return ResultContainer.success(chat)
    }
}
