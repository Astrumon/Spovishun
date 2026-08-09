package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.ua.astrumon.domain.bot.config.ReadinessConfig
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.util.ReadinessRenderer
import com.ua.astrumon.presentation.util.chatLogContextSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * Drives the lifecycle of a readiness poll: opens it on a fresh Telegram message, folds incoming
 * votes into a re-render, and freezes the message once the TTL expires (spovishun-119).
 *
 * Owning all three phases in one place keeps the callback handler free of scheduling concerns and
 * gives the three entry points (`/ping <group>`, `/all`, the `/ping` picker) a single `start` call.
 */
class ReadinessSessionRunner(
    private val store: ReadinessSessionStore,
    private val scope: CoroutineScope,
    private val config: ReadinessConfig,
) {
    private val logger = LoggerFactory.getLogger(ReadinessSessionRunner::class.java)

    /** Pending re-renders, one per poll — a burst of taps collapses into a single edit. */
    private val renderJobs = ConcurrentHashMap<SessionKey, Job>()

    /**
     * Publishes the poll and schedules its expiry. Falls back to a plain message when Telegram does
     * not return the sent message — without an id there is nothing to key a session on.
     *
     * [initiatorId] opened the poll and is already counted as ready in the message that announces
     * it (spovishun-183): asking the person who called everyone together to also tap 👍 adds a step,
     * and until they take it the tally reads one short. The vote is applied only when
     * [ReadinessSession.isEligible] says so — pinging a group you do not belong to, or tapping the
     * `/ping` picker as a bystander, opens the poll with nobody voted for.
     *
     * It happens here rather than at the three entry points because this is the one funnel all of
     * them already pass through; three call sites would be three chances to forget.
     */
    fun start(
        bot: Bot,
        chatId: Long,
        session: ReadinessSession,
        initiatorId: Long,
    ) {
        // Applied before the first send, not as a follow-up edit: an extra editMessageText would
        // spend a rate-limit slot to say what the opening message could have said itself. A picker
        // tap or an `/all` from outside the pinged roster is not a vote — `isEligible` keeps the
        // bystander out, and re-voting needs no special case since `withVote` overwrites.
        val opened = if (session.isEligible(initiatorId)) session.withVote(initiatorId, ReadinessVote.ACCEPTED) else session

        val messageId = bot
            .sendMessage(
                chatId = ChatId.fromId(chatId),
                text = ReadinessRenderer.renderActive(opened),
                parseMode = ParseMode.HTML,
                replyMarkup = voteKeyboard(session.messages),
            ).getOrNull()
            ?.messageId

        if (messageId == null) {
            logger.error("Readiness poll not started: Telegram returned no message id")
            return
        }

        val key = SessionKey(chatId, messageId)
        store.open(key, opened)
        // The scope carries no chat of its own, so the expiry would log as `system` — hand it the
        // caller's context, which is the command that opened the poll (spovishun-168).
        scope.launch(chatLogContextSnapshot()) {
            delay(config.readinessTtl)
            finalize(bot, key)
        }
    }

    /** Whether a poll is still accepting votes — lets a caller tell "not invited" from "already over". */
    fun isLive(key: SessionKey): Boolean = store.get(key) != null

    /**
     * Records a vote and schedules the coalesced re-render.
     *
     * Returns that render as a [Job] so the caller can hold Telegram's callback answer until the
     * roster actually shows the vote — the client keeps its spinner on the tapped button until then,
     * which is the only "working on it" affordance the Bot API offers. Null means the poll is not
     * live or the tapper was never invited.
     */
    fun onVote(
        bot: Bot,
        key: SessionKey,
        userId: Long,
        vote: ReadinessVote,
    ): Job? {
        store.vote(key, userId, vote) ?: return null
        val render = scope.launch(chatLogContextSnapshot()) {
            delay(COALESCE_WINDOW)
            val current = store.get(key) ?: return@launch
            bot.editInPlace(key.chatId, key.messageId, ReadinessRenderer.renderActive(current), voteKeyboard(current.messages))
        }
        renderJobs.put(key, render)?.cancel()
        return render
    }

    /**
     * Closes the poll and rewrites its message with the summary and no keyboard. Idempotent.
     *
     * The three steps are ordered to guarantee the summary is the **last** edit. Closing first makes
     * [ReadinessSessionStore.vote] reject everything from here on, so no further render can be
     * scheduled; joining then drains the one render that may already be past its delay and about to
     * edit. Cancelling alone would not be enough — a coroutine that has left the `delay` is no longer
     * at a cancellation point, so it would repaint the live roster (keyboard and all) over the frozen
     * summary, leaving buttons that answer "poll is over" to every tap.
     */
    suspend fun finalize(
        bot: Bot,
        key: SessionKey,
    ) {
        val session = store.close(key) ?: return
        renderJobs.remove(key)?.cancelAndJoin()
        bot.editInPlace(key.chatId, key.messageId, ReadinessRenderer.renderFinal(session), keyboard = null)
    }

    private fun voteKeyboard(messages: BotMessages): InlineKeyboardMarkup = InlineKeyboardMarkup.create(
        listOf(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = messages.ping.readiness.buttonAccept,
                    callbackData = "${ReadinessCallback.PREFIX}${ReadinessCallback.ACCEPT}",
                ),
                InlineKeyboardButton.CallbackData(
                    text = messages.ping.readiness.buttonDecline,
                    callbackData = "${ReadinessCallback.PREFIX}${ReadinessCallback.DECLINE}",
                ),
            ),
        ),
    )

    private companion object {
        /** Absorbs a burst of taps into one edit, keeping well clear of Telegram's per-chat edit rate. */
        val COALESCE_WINDOW = 1_500.milliseconds
    }
}
