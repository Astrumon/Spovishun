package com.ua.astrumon.presentation.bot.handler

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory registry of live readiness polls (spovishun-119).
 *
 * Sessions are deliberately not persisted: a poll only makes sense while its Telegram message is
 * still the current call to action, and a restart is rare enough that a dead keyboard (answered with
 * "session is over") is an acceptable outcome.
 *
 * Concurrency: [ConcurrentHashMap.compute] applies the update atomically per key and
 * [ReadinessSession] is immutable, so simultaneous taps cannot lose a vote without an explicit lock.
 */
class ReadinessSessionStore {
    private val sessions = ConcurrentHashMap<SessionKey, ReadinessSession>()

    fun open(
        key: SessionKey,
        session: ReadinessSession,
    ) {
        sessions[key] = session
    }

    fun get(key: SessionKey): ReadinessSession? = sessions[key]

    /**
     * Records [vote], or returns null when the poll is over or [userId] was never invited.
     *
     * `computeIfPresent` hands back the stored session either way — unchanged for a bystander's tap —
     * so the returned session alone cannot say whether the vote counted. The eligibility re-check is
     * what maps "session left untouched" to a rejection; it is load-bearing, not defensive.
     */
    fun vote(
        key: SessionKey,
        userId: Long,
        vote: ReadinessVote,
    ): ReadinessSession? = sessions
        .computeIfPresent(key) { _, session ->
            if (session.isEligible(userId)) session.withVote(userId, vote) else session
        }?.takeIf { it.isEligible(userId) }

    /** Ends the poll and returns its final state, or null when it was already closed. */
    fun close(key: SessionKey): ReadinessSession? = sessions.remove(key)
}
