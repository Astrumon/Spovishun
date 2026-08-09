package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.controller.MembersController
import com.ua.astrumon.presentation.toText

class MembersCommand(
    private val membersController: MembersController,
    private val messagesProvider: BotMessagesProvider,
) : BotCommand {
    override val name = "members"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        // Only the chat is needed to list members; the sender guard stays because an update with no
        // sender is a channel post, not a command anyone issued.
        val (chatId, _, _) = update.messageContext() ?: return
        val messages = messagesProvider.forChat(chatId)

        val text = membersController
            .getMembers(chatId)
            .toText(messages, onError = { messages.error.loadMembers(it) })

        bot.reply(chatId, text)
    }
}
