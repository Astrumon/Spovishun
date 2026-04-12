package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.toText
import org.slf4j.LoggerFactory

class DeleteGroupCommand(
    private val groupController: GroupController,
) : BotCommand {

    override val name = "delgroup"

    private val logger = LoggerFactory.getLogger(DeleteGroupCommand::class.java)

    override suspend fun execute(bot: Bot, update: Update) {
        val (chatId, userId, args) = update.messageContext() ?: return

        logger.info("DeleteGroup command invoked - chatId: {}, userId: {}, args: {}", chatId, userId, args)

        val text = groupController.deleteGroup(chatId = chatId, userId = userId, args = args).toText(
            successPrefix = "🗑 ",
            onAccessDenied = { "🚫 Лише адміни та модератори." },
            onNotFound = { "❌ Групу '${it.identifier}' не знайдено." },
        )

        bot.reply(chatId, text)
    }
}
