package presentation.bot.handler

import com.github.kotlintelegrambot.entities.CallbackQuery
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.ua.astrumon.presentation.bot.handler.CallbackContext
import com.ua.astrumon.presentation.bot.handler.Clicker

/** Query id every context factory below stamps — handlers only echo it back when they ack. */
internal const val TEST_QUERY_ID = "cb"

/**
 * Builds the [CallbackContext] a handler now receives — the router owns the parsing, so handler tests
 * hand the parsed value over directly instead of round-tripping through an [Update].
 */
internal fun callbackContext(
    chatId: Long,
    clickerId: Long,
    payload: String,
    messageId: Long = 5L,
): CallbackContext = CallbackContext(
    queryId = TEST_QUERY_ID,
    chatId = chatId,
    messageId = messageId,
    clicker = Clicker(id = clickerId, username = "user_$clickerId", firstName = "A"),
    payload = payload,
)

/** Builds a callback-query [Update] for router tests — shared to avoid repeating the wiring per test. */
internal fun callbackUpdate(
    chatId: Long,
    clickerId: Long,
    data: String,
    messageId: Long = 5L,
    callbackId: String = "cb",
): Update {
    val chat = Chat(id = chatId, type = "group")
    val message = Message(messageId = messageId, date = 0L, chat = chat)
    val callbackQuery = CallbackQuery(
        id = callbackId,
        from = User(id = clickerId, isBot = false, firstName = "A"),
        message = message,
        inlineMessageId = null,
        data = data,
        chatInstance = "i",
    )
    return Update(updateId = 1L, callbackQuery = callbackQuery)
}
