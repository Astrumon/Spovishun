package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.ua.astrumon.presentation.bot.BotMessages

/**
 * Whether a tap on this handler's buttons *starts* an interaction or *continues* one already open.
 *
 * The two differ in more than wording, which is why one property carries both consequences.
 */
enum class CallbackKind {
    /**
     * A fresh entry point — a picker opened by a command. [CallbackRouter] answers the query up
     * front and registers the tapper, exactly as it would for a command.
     */
    ENTRY_POINT,

    /**
     * A tap inside a live interaction, such as a readiness vote. The handler owns the
     * acknowledgement, because *when* it answers is the UI; and the router does not register the
     * tapper, because a bystander poking at someone else's open poll is not an arrival.
     */
    IN_PLACE,
}

/**
 * Handles one inline-callback prefix.
 *
 * [handle] receives a fully resolved [CallbackContext] and the chat's [BotMessages]: the router
 * already parsed the update to pick this handler, so parsing it a second time here was pure
 * duplication — and an acknowledgement each handler could silently forget (spovishun-172).
 */
interface CallbackHandler {
    val prefix: String

    /**
     * Defaults to [CallbackKind.ENTRY_POINT], so a new picker is acked and registers its tapper
     * without opting in. Override only for a mid-interaction control — see [ReadinessCallbackHandler].
     */
    val kind: CallbackKind get() = CallbackKind.ENTRY_POINT

    suspend fun handle(
        bot: Bot,
        ctx: CallbackContext,
        messages: BotMessages,
    )
}
