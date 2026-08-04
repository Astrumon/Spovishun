package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.util.withChatLogContext

class CallbackRouter(
    private val handlers: List<CallbackHandler>,
) {
    suspend fun route(
        bot: Bot,
        update: Update,
    ) {
        val callbackQuery = update.callbackQuery ?: return
        val data = callbackQuery.data ?: return
        val chat = callbackQuery.message?.chat

        // Wraps the whole dispatch so the handler, its controller and every query it makes log
        // against the chat the button was tapped in (spovishun-168).
        withChatLogContext(chat?.id, chat?.type) {
            val handler = handlers.firstOrNull { data.startsWith(it.prefix) }
            if (handler == null) {
                bot.answerCallbackQuery(callbackQuery.id) // unknown prefix: silent ack, no dispatch
                return@withChatLogContext
            }

            handler.handle(bot, update)
        }
    }
}
