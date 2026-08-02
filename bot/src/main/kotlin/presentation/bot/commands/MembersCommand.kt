package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.controller.MembersController
import com.ua.astrumon.presentation.toText
import com.ua.astrumon.presentation.util.BotAdminUtils

class MembersCommand(
    private val membersController: MembersController,
    private val botAdminUtils: BotAdminUtils,
    private val messagesProvider: BotMessagesProvider,
) : BotCommand {
    override val name = "members"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val user = update.message?.from ?: return
        val chatId = update.message?.chat?.id ?: return
        val messages = messagesProvider.forChat(chatId)

        val member = Member(
            id = 0,
            userId = user.id,
            username = user.username ?: "user_${user.id}",
            firstName = user.firstName ?: "Unknown",
        )
        val userRole = botAdminUtils.getMemberRole(bot, chatId, user.id)

        val text = membersController
            .getMembers(chatId, member, userRole)
            .toText(messages, onError = { messages.error.loadMembers(it) })

        bot.reply(chatId, text)
    }
}
