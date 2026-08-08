package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.bot.handler.PickerCopy
import com.ua.astrumon.presentation.bot.handler.RandomCallback
import com.ua.astrumon.presentation.bot.handler.sendPicker
import com.ua.astrumon.presentation.controller.PickerListing
import com.ua.astrumon.presentation.controller.RandomController
import com.ua.astrumon.presentation.toText

class RandomCommand(
    private val randomController: RandomController,
    private val messagesProvider: BotMessagesProvider,
) : BotCommand {
    override val name = "random"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val (chatId, _, args) = update.messageContext() ?: return
        val messages = messagesProvider.forChat(chatId)

        if (args.isEmpty()) {
            showGroupPicker(bot, chatId)
            return
        }

        val text = randomController.pickRandomFromGroup(chatId, args[0].lowercase()).toText(
            messages,
            onNotFound = {
                val available = it.available.joinToString(", ") { k -> k.escapeHtml() }.ifEmpty { "—" }
                messages.error.groupNotFoundHtml(it.identifier.escapeHtml(), available)
            },
        )

        bot.reply(chatId, text)
    }

    /** Args-less flow: group keyboard, or a straight whole-chat pick when the chat has no groups yet. */
    private suspend fun showGroupPicker(
        bot: Bot,
        chatId: Long,
    ) {
        val messages = messagesProvider.forChat(chatId)
        val listing = randomController.groupsForPicker(chatId)
        if (listing is PickerListing.Show && listing.options.isEmpty()) {
            bot.reply(chatId, randomController.pickRandomAll(chatId).toText(messages))
            return
        }
        bot.sendPicker(
            chatId,
            listing,
            PickerCopy(messages, messages.random.menuPrompt, messages.random.noGroups),
        ) { "${RandomCallback.PREFIX}${it.id}" }
    }
}
