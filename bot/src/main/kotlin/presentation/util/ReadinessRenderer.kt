package com.ua.astrumon.presentation.util

import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.handler.ReadinessSession
import com.ua.astrumon.presentation.bot.handler.ReadinessVote

/**
 * Renders a readiness poll body (ParseMode.HTML). Pure — the caller owns delivery.
 *
 * The header arrives already assembled and escaped by the controller; mentions come from
 * [toHtmlMention], which escapes the display name it embeds.
 */
object ReadinessRenderer {
    /** The live poll is the roster alone — the keyboard under it already says what to do. */
    fun renderActive(session: ReadinessSession): String = body(session)

    /** Same body, plus the tally that survives once the keyboard is gone. */
    fun renderFinal(session: ReadinessSession): String = body(session) + summary(session)

    private fun body(session: ReadinessSession): String = session.header + "\n\n" + roster(session)

    private fun summary(session: ReadinessSession): String = BotMessages.Ping.Readiness.summary(
        accepted = session.membersVoting(ReadinessVote.ACCEPTED).size,
        declined = session.membersVoting(ReadinessVote.DECLINED).size,
        pending = session.membersWithoutVote().size,
    )

    private fun roster(session: ReadinessSession): String = session.members.joinToString("\n") { member ->
        BotMessages.Ping.Readiness.rosterItem(statusIcon(session.votes[member.userId]), member.toHtmlMention())
    }

    private fun statusIcon(vote: ReadinessVote?): String = when (vote) {
        ReadinessVote.ACCEPTED -> BotMessages.Ping.Readiness.statusAccepted
        ReadinessVote.DECLINED -> BotMessages.Ping.Readiness.statusDeclined
        null -> BotMessages.Ping.Readiness.statusPending
    }
}
