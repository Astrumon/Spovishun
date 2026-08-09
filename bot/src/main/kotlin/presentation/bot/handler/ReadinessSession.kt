package com.ua.astrumon.presentation.bot.handler

import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.presentation.bot.BotMessages

/** Identifies a readiness poll by the Telegram message that carries its keyboard. */
data class SessionKey(
    val chatId: Long,
    val messageId: Long,
)

enum class ReadinessVote {
    ACCEPTED,
    DECLINED,
}

/**
 * One live readiness poll. Immutable: [withVote] returns a new instance, which is what lets
 * [ReadinessSessionStore] update a session atomically without a lock.
 *
 * [members] holds domain [Member]s so the renderer can reuse `Member.toHtmlMention()` instead of
 * re-deriving mention markup from usernames.
 *
 * [messages] is pinned at open time rather than resolved per render: a poll re-renders on every vote
 * and again at expiry, and a roster that switched language mid-vote would read as a different poll.
 */
data class ReadinessSession(
    val messages: BotMessages,
    val header: String,
    val members: List<Member>,
    val votes: Map<Long, ReadinessVote> = emptyMap(),
) {
    fun isEligible(userId: Long): Boolean = members.any { it.userId == userId }

    /** Re-voting overwrites the previous choice — a member always counts exactly once. */
    fun withVote(
        userId: Long,
        vote: ReadinessVote,
    ): ReadinessSession = copy(votes = votes + (userId to vote))

    fun membersVoting(vote: ReadinessVote): List<Member> = members.filter { votes[it.userId] == vote }

    fun membersWithoutVote(): List<Member> = members.filter { it.userId !in votes }
}
