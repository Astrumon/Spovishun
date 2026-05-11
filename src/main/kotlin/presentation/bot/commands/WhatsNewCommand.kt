package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.controller.WhatsNewController
import com.ua.astrumon.presentation.toText

class WhatsNewCommand(
    private val controller: WhatsNewController,
) : BotCommand {

    override val name = "whatsnew"

    override suspend fun execute(bot: Bot, update: Update) {
        val (chatId, _, args) = update.messageContext() ?: return
        val response = if (args.firstOrNull() == "\$h") controller.showHistory() else controller.showLatest()
        bot.reply(chatId, response.toText(successPrefix = BotMessages.WhatsNew.prefix))
    }
}
