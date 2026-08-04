package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.ua.astrumon.common.util.sanitizeUsername
import com.ua.astrumon.domain.bot.config.ChatAccessConfig
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.presentation.util.BotAdminUtils
import com.ua.astrumon.presentation.util.withChatLogContext
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
        val chat = message.chat

        if (config.allowedChatIds.isNotEmpty() && chat.id !in config.allowedChatIds) return

        withChatLogContext(chat.id, chat.type) { autoRegister(bot, chat, user) }
    }

    private suspend fun autoRegister(
        bot: Bot,
        chat: Chat,
        user: User,
    ) {
        val userRole = botAdminUtils.getMemberRole(bot, chat.id, user.id)

        autoRegisterService
            .ensureUserRegistered(
                chatId = chat.id,
                userId = user.id,
                username = sanitizeUsername(user.username, user.id),
                firstName = user.firstName,
                chatTitle = chat.title,
                chatType = chat.type,
                userRole = userRole,
            ).onSuccess {
                logger.debug("Member auto-register succeeded")
            }.onFailure { error ->
                logger.error("Failed to auto-register member: ${error::class.simpleName}")
            }
    }
}
