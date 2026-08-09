package com.ua.astrumon.presentation.controller

import com.ua.astrumon.domain.bot.model.Member
import com.ua.astrumon.presentation.CommandResponse

/**
 * What a ping resolved to. Mirrors [PickerListing]: it carries the data a readiness poll needs
 * without leaking Telegram types into the controller, and keeps the readiness case out of the
 * universal [CommandResponse.toText].
 */
sealed interface PingOutcome {
    /** Plain ping (readiness disabled), or any failure/empty case — render [response] as text. */
    data class Plain(
        val response: CommandResponse,
    ) : PingOutcome

    /** Readiness poll: [header] is the assembled call to action, [members] are the invitees. */
    data class Readiness(
        val header: String,
        val members: List<Member>,
    ) : PingOutcome
}
