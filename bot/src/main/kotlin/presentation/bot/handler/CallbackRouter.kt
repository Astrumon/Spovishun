package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update

class CallbackRouter(
    private val handlers: List<CallbackHandler>,
) {
    suspend fun route(
        bot: Bot,
        update: Update,
    ) {
        val callbackQuery = update.callbackQuery ?: return
        val data = callbackQuery.data ?: return

        val handler = handlers.firstOrNull { data.startsWith(it.prefix) }
        if (handler == null) {
            bot.answerCallbackQuery(callbackQuery.id) // unknown prefix: silent ack, no dispatch
            return
        }

        handler.handle(bot, update)
    }
}
