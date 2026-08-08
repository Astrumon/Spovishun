package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.util.MemberAutoRegistrar
import com.ua.astrumon.presentation.util.withChatLogContext

/**
 * Turns a raw callback update into a dispatch: picks the handler by prefix, acknowledges the query,
 * registers the tapper, and resolves the chat's copy — once, for every handler (spovishun-172).
 */
class CallbackRouter(
    private val handlers: List<CallbackHandler>,
    private val messagesProvider: BotMessagesProvider,
    private val autoRegistrar: MemberAutoRegistrar,
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

            val ctx = update.callbackContext(handler.prefix) ?: return@withChatLogContext
            if (handler.ackPolicy == AckPolicy.ROUTER) {
                bot.answerCallbackQuery(ctx.queryId)
            }

            // Anyone in the chat may tap a button someone else's command put there, so the tapper is
            // registered here rather than assumed to have arrived through a command.
            callbackQuery.message?.chat?.let { autoRegistrar.ensure(bot, it, callbackQuery.from) }

            handler.handle(bot, ctx, messagesProvider.forChat(ctx.chatId))
        }
    }
}
