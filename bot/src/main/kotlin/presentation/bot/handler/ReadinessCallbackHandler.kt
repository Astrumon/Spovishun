package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessages
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

object ReadinessCallback {
    const val PREFIX = "ready:"
    const val ACCEPT = "a"
    const val DECLINE = "d"
}

/**
 * Readiness poll votes: `ready:a` accepts, `ready:d` declines.
 *
 * The poll is identified by the message the button sits on, so the payload carries no ids and stays
 * far below Telegram's 64-byte callback-data limit. Rejections answer with a toast rather than an
 * edit — a bystander's tap must not disturb the message everyone else is voting on.
 */
class ReadinessCallbackHandler(
    private val runner: ReadinessSessionRunner,
) : CallbackHandler {
    override val prefix = ReadinessCallback.PREFIX

    override suspend fun handle(
        bot: Bot,
        update: Update,
    ) {
        val callbackQuery = update.callbackQuery ?: return
        val ctx = update.callbackContext(prefix) ?: return
        val vote = when (ctx.payload) {
            ReadinessCallback.ACCEPT -> ReadinessVote.ACCEPTED
            ReadinessCallback.DECLINE -> ReadinessVote.DECLINED
            else -> {
                bot.answerCallbackQuery(callbackQuery.id)
                return
            }
        }

        val key = SessionKey(ctx.chatId, ctx.messageId)
        val render = runner.onVote(bot, key, ctx.clickerId, vote)
        if (render == null) {
            val toast = if (runner.isLive(key)) {
                BotMessages.Ping.Readiness.notInvited
            } else {
                BotMessages.Ping.Readiness.sessionClosed
            }
            bot.answerCallbackQuery(callbackQuery.id, text = toast)
            return
        }

        // Leaving the query unanswered is what keeps the button spinning, so the wait is the progress
        // indicator. Capped: a burst of votes keeps pushing the coalesced render back, and a spinner
        // that never stops reads as a broken bot.
        withTimeoutOrNull(SPINNER_CAP) { render.join() }
        bot.answerCallbackQuery(callbackQuery.id)
    }

    private companion object {
        /** Debounce window plus headroom for the edit round trip. */
        val SPINNER_CAP = 3.seconds
    }
}
