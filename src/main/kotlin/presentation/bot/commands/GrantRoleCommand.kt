package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.toText
class GrantRoleCommand(
    private val groupController: GroupController
) : BotCommand {

    override val name = "grantrole"

    override suspend fun execute(bot: Bot, update: Update) {
        val (chatId, userId, args) = update.messageContext() ?: return

        val text = groupController.grantRole(chatId = chatId, userId = userId, args = args).toText(
            successPrefix = "✅ ",
            onAccessDenied = { "🚫 Лише адміни можуть призначати ролі." },
            onNotFound = { "❌ ${it.resource} '${it.identifier}' не знайдено." },
        )

        bot.reply(chatId, text)
    }
}
