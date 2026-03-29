package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.toText
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.util.BotAdminUtils
import org.slf4j.LoggerFactory

class GroupCommand(
    private val groupController: GroupController,
    private val botAdminUtils: BotAdminUtils,
) {
    private val logger = LoggerFactory.getLogger(GroupCommand::class.java)

    suspend fun showGroups(bot: Bot, update: Update) {
        val user = update.message?.from ?: return
        val chatId = update.message?.chat?.id ?: return

        logger.info("Groups command invoked - chatId: {}, userId: {}, username: {}", chatId, user.id, user.username)

        val member = Member(
            id = 0,
            chatId = chatId,
            userId = user.id,
            username = user.username ?: "user_${user.id}",
            firstName = user.firstName ?: "Unknown",
            joinedAt = null,
        )
        val userRole = botAdminUtils.getMemberRole(bot, chatId, user.id)

        val text = groupController.getGroups(chatId, member, userRole)
            .toText(onError = { "❌ Помилка завантаження груп: $it" })

        bot.sendMessage(chatId = ChatId.fromId(chatId), text = text, parseMode = ParseMode.HTML)
    }

    suspend fun addNewGroup(bot: Bot, update: Update) {
        val user = update.message?.from ?: return
        val args = update.message?.text?.split(" ")?.drop(1) ?: emptyList()
        val chatId = update.message?.chat?.id ?: return

        logger.info("NewGroup command invoked - chatId: {}, userId: {}, args: {}", chatId, user.id, args)

        val text = when (val response = groupController.createGroup(chatId = chatId, userId = user.id, args = args)) {
            is CommandResponse.Success -> "✅ ${response.message}"
            is CommandResponse.AccessDenied -> "🚫 Лише адміни та модератори."
            is CommandResponse.NotFound -> "❌ ${response.resource} '${response.identifier}' не знайдено."
            is CommandResponse.Error -> "⚠️ ${response.message}"
        }

        bot.sendMessage(chatId = ChatId.fromId(chatId), text = text, parseMode = ParseMode.HTML)
    }

    suspend fun deleteGroup(bot: Bot, update: Update) {
        val user = update.message?.from ?: return
        val args = update.message?.text?.split(" ")?.drop(1) ?: emptyList()
        val chatId = update.message?.chat?.id ?: return

        logger.info("DeleteGroup command invoked - chatId: {}, userId: {}, args: {}", chatId, user.id, args)

        val text = when (val response = groupController.deleteGroup(chatId = chatId, userId = user.id, args = args)) {
            is CommandResponse.Success -> "🗑 ${response.message}"
            is CommandResponse.AccessDenied -> "🚫 Лише адміни та модератори."
            is CommandResponse.NotFound -> "❌ Групу '${response.identifier}' не знайдено."
            is CommandResponse.Error -> "❌ ${response.message}"
        }

        bot.sendMessage(chatId = ChatId.fromId(chatId), text = text, parseMode = ParseMode.HTML)
    }

    suspend fun addUserToGroup(bot: Bot, update: Update) {
        val user = update.message?.from ?: return
        val args = update.message?.text?.split(" ")?.drop(1) ?: emptyList()
        val chatId = update.message?.chat?.id ?: return

        logger.info("AddUserToGroup command invoked - chatId: {}, userId: {}, args: {}", chatId, user.id, args)

        val text = when (val response = groupController.addUserToGroup(chatId, userId = user.id, args = args)) {
            is CommandResponse.Success -> "✅ ${response.message}"
            is CommandResponse.AccessDenied -> "🚫 Лише адміни та модератори."
            is CommandResponse.NotFound -> "❌ ${response.resource} '${response.identifier}' не знайдено."
            is CommandResponse.Error -> "⚠️ ${response.message}"
        }

        bot.sendMessage(chatId = ChatId.fromId(chatId), text = text, parseMode = ParseMode.HTML)
    }

    suspend fun removeUserFromGroup(bot: Bot, update: Update) {
        val user = update.message?.from ?: return
        val args = update.message?.text?.split(" ")?.drop(1) ?: emptyList()
        val chatId = update.message?.chat?.id ?: return

        logger.info("RemoveUserFromGroup command invoked - chatId: {}, userId: {}, args: {}", chatId, user.id, args)

        val text = when (val response = groupController.removeUserFromGroup(chatId = chatId, userId = user.id, args = args)) {
            is CommandResponse.Success -> "✅ ${response.message}"
            is CommandResponse.AccessDenied -> "🚫 Лише адміни та модератори."
            is CommandResponse.NotFound -> "❌ Групу '${response.identifier}' не знайдено."
            is CommandResponse.Error -> "⚠️ ${response.message}"
        }

        bot.sendMessage(chatId = ChatId.fromId(chatId), text = text, parseMode = ParseMode.HTML)
    }
}
