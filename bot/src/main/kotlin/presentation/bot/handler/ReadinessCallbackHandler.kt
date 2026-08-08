package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
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

    /**
     * The one handler that answers for itself: *when* the query is answered is the UI here. Leaving
     * it pending is what keeps the button spinning while the vote is folded in, and a rejection is
     * delivered as the answer's toast text. A router ack before dispatch would erase both.
     */
    override val ackPolicy = AckPolicy.HANDLER

    override suspend fun handle(
        bot: Bot,
        ctx: CallbackContext,
        messages: BotMessages,
    ) {
        val vote = when (ctx.payload) {
            ReadinessCallback.ACCEPT -> ReadinessVote.ACCEPTED
            ReadinessCallback.DECLINE -> ReadinessVote.DECLINED
            else -> {
                bot.answerCallbackQuery(ctx.queryId)
                return
            }
        }

        val key = SessionKey(ctx.chatId, ctx.messageId)
        val render = runner.onVote(bot, key, ctx.clicker.id, vote)
        if (render == null) {
            val toast = if (runner.isLive(key)) {
                messages.ping.readiness.notInvited
            } else {
                messages.ping.readiness.sessionClosed
            }
            bot.answerCallbackQuery(ctx.queryId, text = toast)
            return
        }

        // Leaving the query unanswered is what keeps the button spinning, so the wait is the progress
        // indicator. Capped: a burst of votes keeps pushing the coalesced render back, and a spinner
        // that never stops reads as a broken bot.
        withTimeoutOrNull(SPINNER_CAP) { render.join() }
        bot.answerCallbackQuery(ctx.queryId)
    }

    private companion object {
        /** Debounce window plus headroom for the edit round trip. */
        val SPINNER_CAP = 3.seconds
    }
}
