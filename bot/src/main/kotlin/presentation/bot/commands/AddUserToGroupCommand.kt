package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.bot.handler.AddToGroupCallback
import com.ua.astrumon.presentation.bot.handler.PickerCopy
import com.ua.astrumon.presentation.bot.handler.sendPicker
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.toText

class AddUserToGroupCommand(
    private val groupController: GroupController,
    private val messagesProvider: BotMessagesProvider,
) : BotCommand {
    override val name = "addtogroup"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val (chatId, userId, args) = update.messageContext() ?: return
        val messages = messagesProvider.forChat(chatId)

        if (args.isEmpty()) {
            bot.sendPicker(
                chatId,
                groupController.groupsForModeratorPicker(chatId, userId),
                PickerCopy(messages, messages.picker.groupPromptAddTo, messages.group.empty, messages.error.onlyAdminsModerators),
            ) { "${AddToGroupCallback.PREFIX}${it.id}" }
            return
        }

        val text = groupController.addUserToGroup(chatId, userId = userId, args = args).toText(
            messages,
            successPrefix = messages.success.prefix,
            onError = { messages.success.warning(it) },
            onAccessDenied = { messages.error.onlyAdminsModerators },
            onNotFound = { messages.error.resourceNotFound(it.resource, it.identifier) },
        )

        bot.reply(chatId, text)
    }
}
