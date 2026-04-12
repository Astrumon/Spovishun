package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.util.BotAdminUtils
import com.ua.astrumon.domain.service.AutoRegisterService
import org.slf4j.LoggerFactory

class MessageHandler(
    private val autoRegisterService: AutoRegisterService,
    private val botAdminUtils: BotAdminUtils
) {
    private val logger = LoggerFactory.getLogger(MessageHandler::class.java)

    suspend fun handleIncomingMessage(bot: Bot, update: Update) {
        val message = update.message ?: return
        val user = message.from ?: return
        val chatId = message.chat.id
        
        val username = user.username ?: "user_${user.id}"
        val firstName = user.firstName

        val userRole = botAdminUtils.getMemberRole(bot, chatId, user.id)
        
        autoRegisterService.ensureUserRegistered(
            chatId = chatId,
            userId = user.id,
            username = username,
            firstName = firstName,
            chatTitle = message.chat.title,
            chatType = message.chat.type,
            userRole = userRole,
        ).onSuccess { member ->
            logger.debug("User ${member.username} is registered (ID: ${member.id})")
        }.onFailure { error ->
            logger.error("Failed to auto-register user ${username}", error)
        }
    }
}
