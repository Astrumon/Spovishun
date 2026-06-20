package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.common.util.sanitizeUsername
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.toText
import com.ua.astrumon.presentation.util.BotAdminUtils

object PingCallback {
    const val PREFIX = "ping:"
}

class PingCallbackHandler(
    private val pingController: PingController,
    private val botAdminUtils: BotAdminUtils,
) {
    suspend fun handle(
        bot: Bot,
        update: Update,
    ) {
        val callbackQuery = update.callbackQuery ?: return
        val data = callbackQuery.data ?: return
        if (!data.startsWith(PingCallback.PREFIX)) return

        bot.answerCallbackQuery(callbackQuery.id)

        val groupId = data.removePrefix(PingCallback.PREFIX).toLongOrNull() ?: return
        val message = callbackQuery.message ?: return
        val chatId = message.chat.id

        val user = callbackQuery.from
        val username = sanitizeUsername(user.username, user.id)
        val userRole = botAdminUtils.getMemberRole(bot, chatId, user.id)

        val text = pingController.pingGroupById(chatId, user.id, username, user.firstName, userRole, groupId).toText(
            onNotFound = { BotMessages.Error.groupNotFoundHtml(it.identifier.escapeHtml(), "—") },
        )

        runCatching {
            bot.deleteMessage(
                chatId = ChatId.fromId(chatId),
                messageId = message.messageId,
            )
        }

        bot.sendMessage(chatId = ChatId.fromId(chatId), text = text, parseMode = ParseMode.HTML)
    }
}
