package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.ua.astrumon.presentation.controller.PickerOption
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("presentation.bot.handler.PickerKeyboard")

/** Builds a one-button-per-row inline keyboard from picker options; [callbackData] encodes each option. */
internal fun pickerKeyboard(
    options: List<PickerOption>,
    callbackData: (PickerOption) -> String,
): InlineKeyboardMarkup = InlineKeyboardMarkup.create(
    options.map { option ->
        listOf(InlineKeyboardButton.CallbackData(text = option.label, callbackData = callbackData(option)))
    },
)

/**
 * Advances a multi-step picker by removing the message that held the tapped button and sending the next
 * one with a fresh keyboard. Delete + send rather than [editInPlace] because each picker step carries a
 * different prompt and the flow reads better as a single message that keeps moving to the bottom.
 */
internal fun Bot.replaceWithKeyboard(
    chatId: Long,
    messageId: Long,
    text: String,
    keyboard: InlineKeyboardMarkup,
) {
    runCatching { deleteMessage(chatId = ChatId.fromId(chatId), messageId = messageId) }
    sendMessage(chatId = ChatId.fromId(chatId), text = text, parseMode = ParseMode.HTML, replyMarkup = keyboard)
}

/**
 * Rewrites a message in place, keeping its id and position in the chat — required by the readiness
 * poll, where re-sending would re-notify every mentioned member and invalidate the session key.
 * A null [keyboard] drops the inline keyboard, which is how a finished poll is frozen.
 *
 * `editMessageText` returns `Pair<retrofit2.Response<…>, Exception?>`; retrofit is a compileOnly
 * dependency of :bot purely so this signature resolves (see `bot/build.gradle.kts`). It reports an
 * API rejection through that second element rather than by throwing, so both channels are checked.
 *
 * A failure is not fatal — the poll stays usable and the next vote repaints it — but it must not pass
 * silently: a swallowed edit looks exactly like "nobody is voting".
 */
internal fun Bot.editInPlace(
    chatId: Long,
    messageId: Long,
    text: String,
    keyboard: InlineKeyboardMarkup?,
) {
    val failure = runCatching {
        editMessageText(
            chatId = ChatId.fromId(chatId),
            messageId = messageId,
            text = text,
            parseMode = ParseMode.HTML,
            replyMarkup = keyboard,
        ).second
    }.fold(onSuccess = { apiError -> apiError }, onFailure = { thrown -> thrown })

    failure?.let { logger.warn("Failed to edit message in place: {}", it::class.simpleName) }
}

/** Terminates a picker flow: removes the button message and sends the final text with no keyboard. */
internal fun Bot.replaceWithText(
    chatId: Long,
    messageId: Long,
    text: String,
) {
    runCatching { deleteMessage(chatId = ChatId.fromId(chatId), messageId = messageId) }
    sendMessage(chatId = ChatId.fromId(chatId), text = text, parseMode = ParseMode.HTML)
}
