package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.common.util.sanitizeUsername
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.handler.PickerCopy
import com.ua.astrumon.presentation.bot.handler.PingCallback
import com.ua.astrumon.presentation.bot.handler.sendPicker
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
            val listing = pingController.groupsForPicker(chatId, user.id, username, user.firstName, userRole)
            bot.sendPicker(
                chatId,
                listing,
                PickerCopy(BotMessages.Ping.menuPrompt, BotMessages.Ping.noGroups),
            ) { "${PingCallback.PREFIX}${it.id}" }
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
