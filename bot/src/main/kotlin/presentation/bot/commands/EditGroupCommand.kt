package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.controller.GroupSettingsController
import com.ua.astrumon.presentation.toText

class EditGroupCommand(
    private val groupSettingsController: GroupSettingsController,
    private val messagesProvider: BotMessagesProvider,
) : BotCommand {
    override val name = "editg"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val (chatId, userId, args) = update.messageContext() ?: return
        val messages = messagesProvider.forChat(chatId)

        val text = groupSettingsController.editGroup(chatId = chatId, userId = userId, args = args).toText(
            messages,
            successPrefix = messages.success.prefix,
            onAccessDenied = { messages.error.onlyAdminsModerators },
            onNotFound = { messages.error.resourceNotFound(it.resource, it.identifier) },
        )

        bot.reply(chatId, text)
    }
}
