package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.toText
class NewGroupCommand(
    private val groupController: GroupController,
) : BotCommand {

    override val name = "newgroup"

    override suspend fun execute(bot: Bot, update: Update) {
        val (chatId, userId, args) = update.messageContext() ?: return

        val text = groupController.createGroup(chatId = chatId, userId = userId, args = args).toText(
            successPrefix = "✅ ",
            onError = { "⚠️ $it" },
            onAccessDenied = { "🚫 Лише адміни та модератори." },
            onNotFound = { "❌ ${it.resource} '${it.identifier}' не знайдено." },
        )

        bot.reply(chatId, text)
    }
}
