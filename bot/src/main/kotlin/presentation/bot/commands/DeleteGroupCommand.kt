package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.handler.DeleteGroupCallback
import com.ua.astrumon.presentation.bot.handler.PickerCopy
import com.ua.astrumon.presentation.bot.handler.sendPicker
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.toText

class DeleteGroupCommand(
    private val groupController: GroupController,
) : BotCommand {
    override val name = "delgroup"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val (chatId, userId, args) = update.messageContext() ?: return

        if (args.isEmpty()) {
            bot.sendPicker(
                chatId,
                groupController.groupsForModeratorPicker(chatId, userId),
                PickerCopy(BotMessages.Picker.groupPromptDel, BotMessages.Group.empty, BotMessages.Error.onlyAdminsModerators),
            ) { "${DeleteGroupCallback.PREFIX}${it.id}" }
            return
        }

        val text = groupController.deleteGroup(chatId = chatId, userId = userId, args = args).toText(
            successPrefix = BotMessages.Success.deletePrefix,
            onAccessDenied = { BotMessages.Error.onlyAdminsModerators },
            onNotFound = { BotMessages.Error.groupNotFound(it.identifier) },
        )

        bot.reply(chatId, text)
    }
}
