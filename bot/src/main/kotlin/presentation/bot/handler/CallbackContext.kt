package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.entities.Update

/** Parsed inline-callback essentials shared by every picker handler. */
internal data class CallbackContext(
    val chatId: Long,
    val messageId: Long,
    val clickerId: Long,
    val payload: String,
)

/** Extracts [CallbackContext] from an update, with [prefix] stripped from the callback data. */
internal fun Update.callbackContext(prefix: String): CallbackContext? {
    val callbackQuery = callbackQuery ?: return null
    val data = callbackQuery.data ?: return null
    val message = callbackQuery.message ?: return null
    return CallbackContext(
        chatId = message.chat.id,
        messageId = message.messageId,
        clickerId = callbackQuery.from.id,
        payload = data.removePrefix(prefix),
    )
}
