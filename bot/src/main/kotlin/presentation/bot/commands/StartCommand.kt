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
import org.slf4j.LoggerFactory
import java.io.IOException

class StartCommand(
    private val registrationController: RegistrationController,
    private val messagesProvider: BotMessagesProvider,
) : BotCommand {
    override val name = "start"

    private val logger = LoggerFactory.getLogger(StartCommand::class.java)

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        // The caller is not read beyond this guard — registration is the dispatch decorator's job
        // now — but an update with no sender is still not a `/start` anyone issued.
        val (chatId, _, _) = update.messageContext() ?: return
        val messages = messagesProvider.forChat(chatId)

        // A group start pre-registers the admins so role gates work before anyone has spoken. It is
        // best-effort: whatever Telegram will not tell us must not cost the caller their welcome.
        // Narrow rather than broad: `getChat` returns a `TelegramBotResult`, and `runApiOperation`
        // has already folded every exception from the send into that value — `getOrNull()` yields
        // null on any API error without throwing. Only reading the error body of an unsuccessful
        // response escapes as an [IOException].
        val chatType = try {
            bot.getChat(ChatId.fromId(chatId)).getOrNull()?.type
        } catch (e: IOException) {
            logger.error("Could not read chat info: ${e::class.simpleName}")
            null
        }

        if (chatType in GROUP_CHAT_TYPES) {
            preregisterAdmins(bot, chatId)
            if (chatType == GROUP_CHAT_TYPE) {
                bot.reply(chatId, messages.welcome.invitation)
            }
        }

        bot.reply(chatId, registrationController.start(chatId).toText(messages))
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
        // Same shape as the `getChat` call above — see the note there for why [IOException] is the
        // only type worth naming.
        val admins = try {
            bot.getChatAdministrators(ChatId.fromId(chatId)).getOrNull()
        } catch (e: IOException) {
            logger.error("Could not read chat administrators: ${e::class.simpleName}")
            null
        }

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
