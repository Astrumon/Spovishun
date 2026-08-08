package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.common.util.UsernameInputSanitizer

/**
 * Who tapped the button. [username] is already sanitized — every consumer needs the safe form and
 * none needs the raw one, so the sanitizing happens once here rather than in each handler.
 */
data class Clicker(
    val id: Long,
    val username: String,
    val firstName: String,
)

/**
 * Everything a callback handler needs from an inline-callback update, resolved once by
 * [CallbackRouter] before dispatch (spovishun-172).
 *
 * [queryId] is carried because acking is the router's job — a handler that owns its own ack
 * ([AckPolicy.HANDLER]) is the exception and needs the id to do it.
 */
data class CallbackContext(
    val queryId: String,
    val chatId: Long,
    val messageId: Long,
    val clicker: Clicker,
    val payload: String,
)

/** Extracts [CallbackContext] from an update, with [prefix] stripped from the callback data. */
internal fun Update.callbackContext(prefix: String): CallbackContext? {
    val callbackQuery = callbackQuery ?: return null
    val data = callbackQuery.data ?: return null
    val message = callbackQuery.message ?: return null
    val user = callbackQuery.from
    return CallbackContext(
        queryId = callbackQuery.id,
        chatId = message.chat.id,
        messageId = message.messageId,
        clicker = Clicker(
            id = user.id,
            username = UsernameInputSanitizer.sanitizeUsername(user.username, user.id),
            firstName = user.firstName,
        ),
        payload = data.removePrefix(prefix),
    )
}

/**
 * A two-step picker payload: `{ownerId}` selects the owning entity, `{ownerId}:{targetId}` acts on a
 * member inside it. Encoding step 1 into step 2's callback data is what keeps the flow stateless.
 */
internal data class TwoStepPayload(
    val ownerId: Long,
    val targetId: Long?,
)

/** Parses [CallbackContext.payload] as a [TwoStepPayload]; null when either id is not a number. */
internal fun CallbackContext.twoStepPayload(): TwoStepPayload? {
    val parts = payload.split(":")
    val ownerId = parts.getOrNull(0)?.toLongOrNull() ?: return null
    if (parts.size == 1) return TwoStepPayload(ownerId, targetId = null)
    val targetId = parts[1].toLongOrNull() ?: return null
    return TwoStepPayload(ownerId, targetId)
}
