package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.common.util.sanitizeUsername
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.handler.PingCallback
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.toText
import com.ua.astrumon.presentation.util.BotAdminUtils

class PingGroupCommand(
    private val pingController: PingController,
    private val botAdminUtils: BotAdminUtils,
) : BotCommand {
    override val name = "ping"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val chatId = update.message?.chat?.id ?: return
        val user = update.message?.from ?: return
        val args = update.message
            ?.text
            ?.split(" ")
            ?.drop(1) ?: emptyList()

        val username = sanitizeUsername(user.username, user.id)
        val userRole = botAdminUtils.getMemberRole(bot, chatId, user.id)

        if (args.isEmpty()) {
            val result = pingController.listGroupsForMenu(chatId, user.id, username, user.firstName, userRole)
            if (result.isFailure) {
                bot.reply(chatId, BotMessages.Error.loadGroupsInternal)
                return
            }
            val groups = result.getOrNull() ?: emptyList()
            if (groups.isEmpty()) {
                bot.reply(chatId, BotMessages.Ping.noGroups)
                return
            }
            val keyboard = InlineKeyboardMarkup.create(
                groups.map { group ->
                    listOf(InlineKeyboardButton.CallbackData(text = group.name, callbackData = "${PingCallback.PREFIX}${group.id}"))
                },
            )
            bot.replyWithKeyboard(chatId, BotMessages.Ping.menuPrompt, keyboard)
            return
        }

        val text = pingController.pingGroup(chatId, user.id, username, user.firstName, userRole, args).toText(
            onNotFound = {
                val available = it.available.joinToString(", ") { k -> k.escapeHtml() }.ifEmpty { "—" }
                BotMessages.Error.groupNotFoundHtml(it.identifier.escapeHtml(), available)
            },
        )

        bot.reply(chatId, text)
    }
}
