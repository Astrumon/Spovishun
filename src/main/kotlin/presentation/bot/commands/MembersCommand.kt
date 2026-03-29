package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.toText
import com.ua.astrumon.presentation.controller.MembersController
import com.ua.astrumon.presentation.util.BotAdminUtils
import org.slf4j.LoggerFactory

class MembersCommand(
    private val membersController: MembersController,
    private val botAdminUtils: BotAdminUtils,
) {
    private val logger = LoggerFactory.getLogger(MembersCommand::class.java)

    suspend operator fun invoke(bot: Bot, update: Update) {
        val user = update.message?.from ?: return
        val chatId = update.message?.chat?.id ?: return

        logger.info("Members command invoked - chatId: {}, userId: {}, username: {}", chatId, user.id, user.username)

        val member = Member(
            id = 0,
            userId = user.id,
            chatId = chatId,
            username = user.username ?: "user_${user.id}",
            firstName = user.firstName ?: "Unknown",
            joinedAt = null,
        )
        val userRole = botAdminUtils.getMemberRole(bot, chatId, user.id)

        val text = membersController.getMembers(chatId, member, userRole)
            .toText(onError = { "❌ Помилка завантаження учасників: $it" })

        bot.sendMessage(chatId = ChatId.fromId(chatId), text = text, parseMode = ParseMode.HTML)
    }
}
