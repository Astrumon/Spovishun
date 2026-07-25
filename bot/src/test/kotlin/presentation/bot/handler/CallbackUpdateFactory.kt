package presentation.bot.handler

import com.github.kotlintelegrambot.entities.CallbackQuery
import com.github.kotlintelegrambot.entities.Chat
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User

/** Builds a callback-query [Update] for handler tests — shared to avoid repeating the wiring per test. */
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
