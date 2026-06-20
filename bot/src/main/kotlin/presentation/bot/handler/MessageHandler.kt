package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.common.util.sanitizeUsername
import com.ua.astrumon.domain.bot.config.ChatAccessConfig
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.presentation.util.BotAdminUtils
import org.slf4j.LoggerFactory

class MessageHandler(
    private val autoRegisterService: AutoRegisterService,
    private val botAdminUtils: BotAdminUtils,
    private val config: ChatAccessConfig,
) {
    private val logger = LoggerFactory.getLogger(MessageHandler::class.java)

    suspend fun handleIncomingMessage(
        bot: Bot,
        update: Update,
    ) {
        val message = update.message ?: return
        val user = message.from ?: return
        val chatId = message.chat.id

        if (config.allowedChatIds.isNotEmpty() && chatId !in config.allowedChatIds) return

        val username = sanitizeUsername(user.username, user.id)
        val firstName = user.firstName

        val userRole = botAdminUtils.getMemberRole(bot, chatId, user.id)

        autoRegisterService
            .ensureUserRegistered(
                chatId = chatId,
                userId = user.id,
                username = username,
                firstName = firstName,
                chatTitle = message.chat.title,
                chatType = message.chat.type,
                userRole = userRole,
            ).onSuccess {
                logger.debug("Member auto-register succeeded")
            }.onFailure { error ->
                logger.error("Failed to auto-register member: ${error::class.simpleName}")
            }
    }
}
