package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.toText

class ShowGroupsCommand(
    private val groupController: GroupController,
    private val messagesProvider: BotMessagesProvider,
) : BotCommand {
    override val name = "groups"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        // Only the chat is needed to list groups; the sender guard stays because an update with no
        // sender is a channel post, not a command anyone issued.
        val (chatId, _, _) = update.messageContext() ?: return
        val messages = messagesProvider.forChat(chatId)

        val text = groupController
            .getGroups(chatId)
            .toText(messages, onError = { messages.error.loadGroups(it) })

        bot.reply(chatId, text)
    }
}
