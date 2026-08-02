package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.controller.BirthdayController
import com.ua.astrumon.presentation.toText

class BirthdayCommand(
    private val birthdayController: BirthdayController,
    private val messagesProvider: BotMessagesProvider,
) : BotCommand {
    override val name = "birthday"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val (chatId, userId, args) = update.messageContext() ?: return
        val messages = messagesProvider.forChat(chatId)

        if (args.isEmpty()) {
            bot.reply(chatId, messages.birthday.usage)
            return
        }

        val response = when {
            args[0].equals("off", ignoreCase = true) -> birthdayController.clearOwnBirthday(messages, userId)
            args.size == 1 -> birthdayController.setOwnBirthday(messages, userId, args[0])
            else -> birthdayController.setBirthdayForOther(chatId, userId, args)
        }

        bot.reply(
            chatId,
            response.toText(
                messages,
                successPrefix = messages.success.prefix,
                onAccessDenied = { messages.error.onlyAdminsModerators },
            ),
        )
    }
}
