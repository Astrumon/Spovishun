package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.domain.model.Member
import com.ua.astrumon.presentation.toText
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.util.BotAdminUtils
import org.slf4j.LoggerFactory

class ShowGroupsCommand(
    private val groupController: GroupController,
    private val botAdminUtils: BotAdminUtils,
) : BotCommand {

    override val name = "groups"

    private val logger = LoggerFactory.getLogger(ShowGroupsCommand::class.java)

    override suspend fun execute(bot: Bot, update: Update) {
        val user = update.message?.from ?: return
        val chatId = update.message?.chat?.id ?: return

        logger.info("Groups command invoked - chatId: {}, userId: {}, username: {}", chatId, user.id, user.username)

        val member = Member(
            id = 0,
            userId = user.id,
            username = user.username ?: "user_${user.id}",
            firstName = user.firstName ?: "Unknown",
        )
        val userRole = botAdminUtils.getMemberRole(bot, chatId, user.id)

        val text = groupController.getGroups(chatId, member, userRole)
            .toText(onError = { "❌ Помилка завантаження груп: $it" })

        bot.reply(chatId, text)
    }
}
