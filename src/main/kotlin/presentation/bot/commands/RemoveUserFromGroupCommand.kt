package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.toText
import org.slf4j.LoggerFactory

class RemoveUserFromGroupCommand(
    private val groupController: GroupController,
) : BotCommand {

    override val name = "removefromgroup"

    private val logger = LoggerFactory.getLogger(RemoveUserFromGroupCommand::class.java)

    override suspend fun execute(bot: Bot, update: Update) {
        val (chatId, userId, args) = update.messageContext() ?: return

        logger.info("RemoveUserFromGroup command invoked - chatId: {}, userId: {}, args: {}", chatId, userId, args)

        val text = groupController.removeUserFromGroup(chatId = chatId, userId = userId, args = args).toText(
            successPrefix = "✅ ",
            onError = { "⚠️ $it" },
            onAccessDenied = { "🚫 Лише адміни та модератори." },
            onNotFound = { "❌ Групу '${it.identifier}' не знайдено." },
        )

        bot.reply(chatId, text)
    }
}
