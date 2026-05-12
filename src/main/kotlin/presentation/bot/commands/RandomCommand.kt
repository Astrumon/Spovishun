package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.common.util.sanitizeUsername
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.controller.RandomController
import com.ua.astrumon.presentation.toText
import com.ua.astrumon.presentation.util.BotAdminUtils

class RandomCommand(
    private val randomController: RandomController,
    private val botAdminUtils: BotAdminUtils,
) : BotCommand {

    override val name = "random"

    override suspend fun execute(bot: Bot, update: Update) {
        val chatId = update.message?.chat?.id ?: return
        val user = update.message?.from ?: return
        val args = update.message?.text?.split(" ")?.drop(1) ?: emptyList()

        val username = sanitizeUsername(user.username, user.id)
        val userRole = botAdminUtils.getMemberRole(bot, chatId, user.id)

        val text = if (args.isEmpty()) {
            randomController.pickRandomAll(chatId, user.id, username, user.firstName, userRole).toText()
        } else {
            randomController.pickRandomFromGroup(chatId, user.id, username, user.firstName, userRole, args[0].lowercase()).toText(
                onNotFound = {
                    val available = it.available.joinToString(", ") { k -> k.escapeHtml() }.ifEmpty { "—" }
                    BotMessages.Error.groupNotFoundHtml(it.identifier.escapeHtml(), available)
                },
            )
        }

        bot.reply(chatId, text)
    }
}
