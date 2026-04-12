package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.toText
import com.ua.astrumon.presentation.util.BotAdminUtils
import org.slf4j.LoggerFactory

class PingGroupCommand(
    private val pingController: PingController,
    private val botAdminUtils: BotAdminUtils,
) : BotCommand {

    override val name = "ping"

    private val logger = LoggerFactory.getLogger(PingGroupCommand::class.java)

    override suspend fun execute(bot: Bot, update: Update) {
        val chatId = update.message?.chat?.id ?: return
        val user = update.message?.from ?: return
        val args = update.message?.text?.split(" ")?.drop(1) ?: emptyList()

        logger.info("PingGroup command invoked - chatId: {}, userId: {}, args: {}", chatId, user.id, args)

        val username = user.username ?: "user_${user.id}"
        val userRole = botAdminUtils.getMemberRole(bot, chatId, user.id)
        val text = pingController.pingGroup(chatId, user.id, username, user.firstName, userRole, args).toText(
            onNotFound = {
                val available = it.available.joinToString(", ").ifEmpty { "—" }
                "Групу <b>${it.identifier}</b> не знайдено.\nДоступні: $available"
            },
        )

        bot.reply(chatId, text)
    }
}
