package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.handler.AddToGroupCallback
import com.ua.astrumon.presentation.bot.handler.deliver
import com.ua.astrumon.presentation.bot.handler.toRender
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.toText

class AddUserToGroupCommand(
    private val groupController: GroupController,
) : BotCommand {
    override val name = "addtogroup"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val (chatId, userId, args) = update.messageContext() ?: return

        if (args.isEmpty()) {
            showGroupPicker(bot, chatId, userId)
            return
        }

        val text = groupController.addUserToGroup(chatId, userId = userId, args = args).toText(
            successPrefix = BotMessages.Success.prefix,
            onError = { BotMessages.Success.warning(it) },
            onAccessDenied = { BotMessages.Error.onlyAdminsModerators },
            onNotFound = { BotMessages.Error.resourceNotFound(it.resource, it.identifier) },
        )

        bot.reply(chatId, text)
    }

    private suspend fun showGroupPicker(
        bot: Bot,
        chatId: Long,
        userId: Long,
    ) {
        val render = groupController.groupsForModeratorPicker(chatId, userId).toRender(
            prompt = BotMessages.Picker.groupPromptAddTo,
            emptyMessage = BotMessages.Group.empty,
            accessDeniedMessage = BotMessages.Error.onlyAdminsModerators,
            callbackData = { "${AddToGroupCallback.PREFIX}${it.id}" },
        )
        bot.deliver(chatId, render)
    }
}
