package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.controller.BirthdayController
import com.ua.astrumon.presentation.toText

class BirthdayCommand(
    private val birthdayController: BirthdayController,
) : BotCommand {

    override val name = "birthday"

    override suspend fun execute(bot: Bot, update: Update) {
        val (chatId, userId, args) = update.messageContext() ?: return

        if (args.isEmpty()) {
            bot.reply(chatId, BotMessages.Birthday.usage)
            return
        }

        val response = when {
            args[0].equals("off", ignoreCase = true) -> birthdayController.clearOwnBirthday(userId)
            args.size == 1 -> birthdayController.setOwnBirthday(userId, args[0])
            else -> birthdayController.setBirthdayForOther(chatId, userId, args)
        }

        bot.reply(chatId, response.toText(
            successPrefix = BotMessages.Success.prefix,
            onAccessDenied = { BotMessages.Error.onlyAdminsModerators }
        ))
    }
}
