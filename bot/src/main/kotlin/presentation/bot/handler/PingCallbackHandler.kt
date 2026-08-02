package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.common.util.sanitizeUsername
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.controller.PingOutcome
import com.ua.astrumon.presentation.toText
import com.ua.astrumon.presentation.util.BotAdminUtils

object PingCallback {
    const val PREFIX = "ping:"
}

/**
 * `/ping` picker: `ping:{groupId}` pings that group, `ping:0`
 * ([PingController.ALL_MEMBERS_ID]) pings every registered member of the chat.
 */
class PingCallbackHandler(
    private val pingController: PingController,
    private val botAdminUtils: BotAdminUtils,
    private val readinessSessionRunner: ReadinessSessionRunner,
    private val messagesProvider: BotMessagesProvider,
) : CallbackHandler {
    override val prefix = PingCallback.PREFIX

    override suspend fun handle(
        bot: Bot,
        update: Update,
    ) {
        val callbackQuery = update.callbackQuery ?: return
        bot.answerCallbackQuery(callbackQuery.id)
        val ctx = update.callbackContext(prefix) ?: return
        val messages = messagesProvider.forChat(ctx.chatId)
        val groupId = ctx.payload.toLongOrNull() ?: return

        val user = callbackQuery.from
        val username = sanitizeUsername(user.username, user.id)
        val userRole = botAdminUtils.getMemberRole(bot, ctx.chatId, user.id)

        val outcome = if (groupId == PingController.ALL_MEMBERS_ID) {
            pingController.pingAll(ctx.chatId, ctx.clickerId, username, user.firstName, userRole, emptyList())
        } else {
            pingController.pingGroupById(ctx.chatId, ctx.clickerId, username, user.firstName, userRole, groupId)
        }

        deliver(bot, ctx, DeliveredOutcome(messages, outcome))
    }

    /** Bundles the poll outcome with the bundle it must render in — [deliver] needs both. */
    private data class DeliveredOutcome(
        val messages: BotMessages,
        val outcome: PingOutcome,
    )

    private fun deliver(
        bot: Bot,
        ctx: CallbackContext,
        delivered: DeliveredOutcome,
    ) = when (val outcome = delivered.outcome) {
        is PingOutcome.Plain -> {
            val text = outcome.response.toText(
                delivered.messages,
                onNotFound = { delivered.messages.error.groupNotFoundHtml(it.identifier.escapeHtml(), "—") },
            )
            bot.replaceWithText(ctx.chatId, ctx.messageId, text)
        }

        // The poll needs its own message to edit in place, so the picker prompt is dropped first.
        is PingOutcome.Readiness -> {
            runCatching { bot.deleteMessage(chatId = ChatId.fromId(ctx.chatId), messageId = ctx.messageId) }
            readinessSessionRunner.start(bot, ctx.chatId, ReadinessSession(delivered.messages, outcome.header, outcome.members))
        }
    }
}
