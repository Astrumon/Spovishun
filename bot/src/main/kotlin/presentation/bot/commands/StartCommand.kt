package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.common.util.UsernameInputSanitizer
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.controller.RegistrationController
import com.ua.astrumon.presentation.controller.RegistrationRequest
import com.ua.astrumon.presentation.toText
import com.ua.astrumon.presentation.util.BotAdminUtils
import org.slf4j.LoggerFactory

class StartCommand(
    private val registrationController: RegistrationController,
    private val botAdminUtils: BotAdminUtils,
    private val messagesProvider: BotMessagesProvider,
) : BotCommand {
    override val name = "start"

    private val logger = LoggerFactory.getLogger(StartCommand::class.java)

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val chatId = update.message?.chat?.id ?: return
        val user = update.message?.from ?: return
        val messages = messagesProvider.forChat(chatId)

        // A group start pre-registers the admins so role gates work before anyone has spoken. It is
        // best-effort: whatever Telegram will not tell us must not cost the caller their welcome.
        val chatType = runCatching { bot.getChat(ChatId.fromId(chatId)).getOrNull()?.type }
            .onFailure { logger.error("Could not read chat info: ${it::class.simpleName}") }
            .getOrNull()

        if (chatType in GROUP_CHAT_TYPES) {
            preregisterAdmins(bot, chatId)
            if (chatType == GROUP_CHAT_TYPE) {
                bot.reply(chatId, messages.welcome.invitation)
            }
        }

        val request = RegistrationRequest(
            chatId = chatId,
            userId = user.id,
            username = UsernameInputSanitizer.sanitizeUsername(user.username, user.id),
            firstName = user.firstName,
            userRole = botAdminUtils.getMemberRole(bot, chatId, user.id),
        )

        bot.reply(chatId, registrationController.start(request).toText(messages))
    }

    /**
     * Registers every current chat administrator as [MemberRole.ADMIN].
     *
     * The catch is scoped to this pass alone — a failure here leaves the admins unregistered, which
     * the next `/start` retries, and must not swallow anything the caller's own registration throws.
     */
    private suspend fun preregisterAdmins(
        bot: Bot,
        chatId: Long,
    ) {
        val admins = runCatching { bot.getChatAdministrators(ChatId.fromId(chatId)).getOrNull() }
            .onFailure { logger.error("Could not read chat administrators: ${it::class.simpleName}") }
            .getOrNull()

        if (admins == null) {
            logger.warn("Failed to get chat administrators")
            return
        }

        admins.forEach { admin ->
            registrationController.ensureUserRegistered(
                RegistrationRequest(
                    chatId = chatId,
                    userId = admin.user.id,
                    username = UsernameInputSanitizer.sanitizeUsername(admin.user.username, admin.user.id),
                    firstName = admin.user.firstName,
                    userRole = MemberRole.ADMIN,
                ),
            )
        }
    }

    private companion object {
        /** Chat types that have administrators worth pre-registering. */
        val GROUP_CHAT_TYPES = setOf("group", "supergroup")

        /** Only a basic group gets the invitation — a supergroup's members are reached another way. */
        const val GROUP_CHAT_TYPE = "group"
    }
}
