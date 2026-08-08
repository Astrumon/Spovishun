package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.ua.astrumon.presentation.bot.BotMessages

/** Who answers Telegram's callback query — see [CallbackHandler.ackPolicy]. */
enum class AckPolicy {
    /** [CallbackRouter] answers before dispatch. The default, and what every handler wants. */
    ROUTER,

    /** The handler answers itself, because *when* it answers is part of the UI. */
    HANDLER,
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
     * Defaults to [AckPolicy.ROUTER], so a new handler is acked without opting in. Override only
     * when the pending query is itself the affordance — see [ReadinessCallbackHandler].
     */
    val ackPolicy: AckPolicy get() = AckPolicy.ROUTER

    suspend fun handle(
        bot: Bot,
        ctx: CallbackContext,
        messages: BotMessages,
    )
}
