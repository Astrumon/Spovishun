package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.controller.WhatsNewController
import com.ua.astrumon.presentation.toText

class WhatsNewCommand(
    private val controller: WhatsNewController,
) : BotCommand {
    override val name = "whatsnew"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val (chatId, userId, args) = update.messageContext() ?: return
        val response = when (args.firstOrNull()) {
            "\$h" -> controller.showHistory()
            "\$on" -> controller.setAnnouncements(chatId, userId, enabled = true)
            "\$off" -> controller.setAnnouncements(chatId, userId, enabled = false)
            else -> controller.showLatest()
        }
        if (response is CommandResponse.Silent) return
        bot.reply(chatId, response.toText(successPrefix = BotMessages.WhatsNew.prefix))
    }
}
