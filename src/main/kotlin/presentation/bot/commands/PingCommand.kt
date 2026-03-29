package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.toText
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.util.BotAdminUtils
import org.slf4j.LoggerFactory

class PingCommand(
    private val pingController: PingController,
    private val botAdminUtils: BotAdminUtils,
) {
    private val logger = LoggerFactory.getLogger(PingCommand::class.java)

    suspend fun pingAll(bot: Bot, update: Update) {
        val chatId = update.message?.chat?.id ?: return
        val user = update.message?.from ?: return
        val args = update.message?.text?.split(" ")?.drop(1) ?: emptyList()

        logger.info("PingAll command invoked - chatId: {}, userId: {}", chatId, user.id)

        val username = user.username ?: "user_${user.id}"
        val userRole = botAdminUtils.getMemberRole(bot, chatId, user.id)
        val text = pingController.pingAll(chatId, user.id, username, user.firstName, userRole, args).toText()

        bot.sendMessage(chatId = ChatId.fromId(chatId), text = text, parseMode = ParseMode.HTML)
    }

    suspend fun pingGroup(bot: Bot, update: Update) {
        val chatId = update.message?.chat?.id ?: return
        val user = update.message?.from ?: return
        val args = update.message?.text?.split(" ")?.drop(1) ?: emptyList()

        logger.info("PingGroup command invoked - chatId: {}, userId: {}, args: {}", chatId, user.id, args)

        val username = user.username ?: "user_${user.id}"
        val userRole = botAdminUtils.getMemberRole(bot, chatId, user.id)
        val text = when (val response = pingController.pingGroup(chatId, user.id, username, user.firstName, userRole, args)) {
            is CommandResponse.Success -> response.message
            is CommandResponse.NotFound -> {
                val available = response.available.joinToString(", ").ifEmpty { "—" }
                "Групу <b>${response.identifier}</b> не знайдено.\nДоступні: $available"
            }
            is CommandResponse.Error -> response.message
            is CommandResponse.AccessDenied -> "🚫 ${response.reason}"
        }

        bot.sendMessage(chatId = ChatId.fromId(chatId), text = text, parseMode = ParseMode.HTML)
    }
}
