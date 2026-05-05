package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.toText
class RemoveUserFromGroupCommand(
    private val groupController: GroupController,
) : BotCommand {

    override val name = "removefromgroup"

    override suspend fun execute(bot: Bot, update: Update) {
        val (chatId, userId, args) = update.messageContext() ?: return

        val text = groupController.removeUserFromGroup(chatId = chatId, userId = userId, args = args).toText(
            successPrefix = BotMessages.Success.prefix,
            onError = { BotMessages.Success.warning(it) },
            onAccessDenied = { BotMessages.Error.onlyAdminsModerators },
            onNotFound = { BotMessages.Error.groupNotFound(it.identifier) },
        )

        bot.reply(chatId, text)
    }
}
