package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.controller.WhatsNewController
import com.ua.astrumon.presentation.toText

class WhatsNewCommand(
    private val controller: WhatsNewController,
    private val messagesProvider: BotMessagesProvider,
) : BotCommand {
    override val name = "whatsnew"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val (chatId, userId, args) = update.messageContext() ?: return
        val messages = messagesProvider.forChat(chatId)
        val response = when (args.firstOrNull()) {
            "\$h" -> controller.showHistory(messages)
            "\$on" -> controller.setAnnouncements(chatId, userId, enabled = true)
            "\$off" -> controller.setAnnouncements(chatId, userId, enabled = false)
            else -> controller.showLatest(messages)
        }
        if (response is CommandResponse.Silent) return
        bot.reply(chatId, response.toText(messages, successPrefix = messages.whatsNew.prefix))
    }
}
