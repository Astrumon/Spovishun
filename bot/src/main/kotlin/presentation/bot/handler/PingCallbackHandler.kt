package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.controller.PingOutcome
import com.ua.astrumon.presentation.toText

object PingCallback {
    const val PREFIX = "ping:"
}

/**
 * `/ping` picker: `ping:{groupId}` pings that group, `ping:0`
 * ([PingController.ALL_MEMBERS_ID]) pings every registered member of the chat.
 */
class PingCallbackHandler(
    private val pingController: PingController,
    private val readinessSessionRunner: ReadinessSessionRunner,
) : CallbackHandler {
    override val prefix = PingCallback.PREFIX

    override suspend fun handle(
        bot: Bot,
        ctx: CallbackContext,
        messages: BotMessages,
    ) {
        val groupId = ctx.payload.toLongOrNull() ?: return

        val outcome = if (groupId == PingController.ALL_MEMBERS_ID) {
            pingController.pingAll(ctx.chatId, emptyList())
        } else {
            pingController.pingGroupById(ctx.chatId, groupId)
        }

        deliver(bot, ctx, messages, outcome)
    }

    private fun deliver(
        bot: Bot,
        ctx: CallbackContext,
        messages: BotMessages,
        outcome: PingOutcome,
    ) = when (outcome) {
        is PingOutcome.Plain -> {
            val text = outcome.response.toText(
                messages,
                onNotFound = { messages.error.groupNotFoundHtml(it.identifier.escapeHtml(), "—") },
            )
            bot.replaceWithText(ctx.chatId, ctx.messageId, text)
        }

        // The poll needs its own message to edit in place, so the picker prompt is dropped first.
        is PingOutcome.Readiness -> {
            runCatching { bot.deleteMessage(chatId = ChatId.fromId(ctx.chatId), messageId = ctx.messageId) }
            readinessSessionRunner.start(bot, ctx.chatId, ReadinessSession(messages, outcome.header, outcome.members))
        }
    }
}
