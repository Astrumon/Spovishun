package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.common.util.sanitizeUsername
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.bot.handler.PickerCopy
import com.ua.astrumon.presentation.bot.handler.RandomCallback
import com.ua.astrumon.presentation.bot.handler.sendPicker
import com.ua.astrumon.presentation.controller.PickerListing
import com.ua.astrumon.presentation.controller.RandomController
import com.ua.astrumon.presentation.toText
import com.ua.astrumon.presentation.util.BotAdminUtils

class RandomCommand(
    private val randomController: RandomController,
    private val botAdminUtils: BotAdminUtils,
    private val messagesProvider: BotMessagesProvider,
) : BotCommand {
    override val name = "random"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val chatId = update.message?.chat?.id ?: return
        val messages = messagesProvider.forChat(chatId)
        val user = update.message?.from ?: return
        val args = update.message
            ?.text
            ?.split(" ")
            ?.drop(1) ?: emptyList()

        val username = sanitizeUsername(user.username, user.id)
        val userRole = botAdminUtils.getMemberRole(bot, chatId, user.id)

        if (args.isEmpty()) {
            showGroupPicker(bot, chatId, user, username, userRole)
            return
        }

        val text = randomController.pickRandomFromGroup(chatId, user.id, username, user.firstName, userRole, args[0].lowercase()).toText(
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
        user: User,
        username: String,
        userRole: MemberRole,
    ) {
        val messages = messagesProvider.forChat(chatId)
        val listing = randomController.groupsForPicker(chatId, user.id, username, user.firstName, userRole)
        if (listing is PickerListing.Show && listing.options.isEmpty()) {
            bot.reply(chatId, randomController.pickRandomAll(chatId, user.id, username, user.firstName, userRole).toText(messages))
            return
        }
        bot.sendPicker(
            chatId,
            listing,
            PickerCopy(messages, messages.random.menuPrompt, messages.random.noGroups),
        ) { "${RandomCallback.PREFIX}${it.id}" }
    }
}
