package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.domain.bot.config.ChatAccessConfig
import com.ua.astrumon.presentation.util.MemberAutoRegistrar
import com.ua.astrumon.presentation.util.withChatLogContext

class MessageHandler(
    private val autoRegistrar: MemberAutoRegistrar,
    private val config: ChatAccessConfig,
) {
    suspend fun handleIncomingMessage(
        bot: Bot,
        update: Update,
    ) {
        val message = update.message ?: return
        val user = message.from ?: return
        val chat = message.chat

        if (config.allowedChatIds.isNotEmpty() && chat.id !in config.allowedChatIds) return

        withChatLogContext(chat) { autoRegistrar.ensure(bot, chat, user) }
    }
}
