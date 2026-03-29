package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.controller.GroupController
import org.slf4j.LoggerFactory

class GrantRoleCommand(
    private val groupController: GroupController
) {
    private val logger = LoggerFactory.getLogger(GrantRoleCommand::class.java)

    suspend operator fun invoke(bot: Bot, update: Update) {
        val user = update.message?.from ?: return
        val args = update.message?.text?.split(" ")?.drop(1) ?: emptyList()
        val chatId = update.message?.chat?.id ?: return

        logger.info("GrantRole command invoked - chatId: {}, userId: {}, args: {}", chatId, user.id, args)

        val text = when (val response = groupController.grantRole(chatId = chatId, userId = user.id, args = args)) {
            is CommandResponse.Success -> "✅ ${response.message}"
            is CommandResponse.AccessDenied -> "🚫 Лише адміни можуть призначати ролі."
            is CommandResponse.NotFound -> "❌ ${response.resource} '${response.identifier}' не знайдено."
            is CommandResponse.Error -> "❌ ${response.message}"
        }

        bot.sendMessage(chatId = ChatId.fromId(chatId), text = text, parseMode = ParseMode.HTML)
    }
}
