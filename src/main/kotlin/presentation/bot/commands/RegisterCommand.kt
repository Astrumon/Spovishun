package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.toText
import com.ua.astrumon.presentation.controller.RegistrationController
import com.ua.astrumon.presentation.util.BotAdminUtils
class RegisterCommand(
    private val registrationController: RegistrationController,
    private val botAdminUtils: BotAdminUtils,
) : BotCommand {

    override val name = "register"

    override suspend fun execute(bot: Bot, update: Update) {
        val user = update.message?.from ?: return
        val chatId = update.message?.chat?.id ?: return

        val username = user.username ?: "user_${user.id}"
        val userRole = botAdminUtils.getMemberRole(bot, chatId, user.id)
        val response = registrationController.register(chatId, user.id, username, user.firstName, userRole)

        val text = response.toText("✅ ")

        bot.reply(chatId, text)
    }
}
