package com.ua.astrumon.presentation.util

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.User
import com.ua.astrumon.common.util.UsernameInputSanitizer
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import org.slf4j.LoggerFactory

/**
 * Registers whoever produced an update, if they are not registered already.
 *
 * One implementation for all three entry paths — plain messages, commands and callback taps — so
 * that "everyone who interacts with the bot ends up in the member table" is a property of the
 * dispatch layer instead of a line each controller had to remember (spovishun-172).
 *
 * The role is passed as a supplier: deriving it costs a blocking `getChatMember`, and
 * [AutoRegisterService.ensureUserRegistered] only needs it when it actually creates a member.
 */
class MemberAutoRegistrar(
    private val autoRegisterService: AutoRegisterService,
    private val botAdminUtils: BotAdminUtils,
) {
    private val logger = LoggerFactory.getLogger(MemberAutoRegistrar::class.java)

    suspend fun ensure(
        bot: Bot,
        chat: Chat,
        user: User,
    ) {
        autoRegisterService
            .ensureUserRegistered(
                chatId = chat.id,
                userId = user.id,
                username = UsernameInputSanitizer.sanitizeUsername(user.username, user.id),
                firstName = user.firstName,
                resolveRole = { botAdminUtils.getMemberRole(bot, chat.id, user.id) },
                chatTitle = chat.title,
                chatType = chat.type,
            ).onSuccess {
                logger.debug("Member auto-register succeeded")
            }.onFailure { error ->
                logger.error("Failed to auto-register member: ${error::class.simpleName}")
            }
    }
}
