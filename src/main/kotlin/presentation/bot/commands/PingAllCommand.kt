package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.toText
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.util.BotAdminUtils
class PingAllCommand(
    private val pingController: PingController,
    private val botAdminUtils: BotAdminUtils,
) : BotCommand {

    override val name = "all"

    override suspend fun execute(bot: Bot, update: Update) {
        val chatId = update.message?.chat?.id ?: return
        val user = update.message?.from ?: return
        val args = update.message?.text?.split(" ")?.drop(1) ?: emptyList()

        val username = user.username ?: "user_${user.id}"
        val userRole = botAdminUtils.getMemberRole(bot, chatId, user.id)
        val text = pingController.pingAll(chatId, user.id, username, user.firstName, userRole, args).toText()

        bot.reply(chatId, text)
    }
}
