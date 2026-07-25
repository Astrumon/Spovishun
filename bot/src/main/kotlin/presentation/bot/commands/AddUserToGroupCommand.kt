package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.handler.AddToGroupCallback
import com.ua.astrumon.presentation.bot.handler.PickerCopy
import com.ua.astrumon.presentation.bot.handler.sendPicker
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
            bot.sendPicker(
                chatId,
                groupController.groupsForModeratorPicker(chatId, userId),
                PickerCopy(BotMessages.Picker.groupPromptAddTo, BotMessages.Group.empty, BotMessages.Error.onlyAdminsModerators),
            ) { "${AddToGroupCallback.PREFIX}${it.id}" }
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
}
