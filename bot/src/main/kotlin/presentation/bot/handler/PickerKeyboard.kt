package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.ua.astrumon.presentation.controller.PickerOption

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
 * one with a fresh keyboard. Delete + send (not `editMessageText`) because the telegram lib exposes an
 * inaccessible `retrofit2.Response` in the edit method's signature.
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

/** Terminates a picker flow: removes the button message and sends the final text with no keyboard. */
internal fun Bot.replaceWithText(
    chatId: Long,
    messageId: Long,
    text: String,
) {
    runCatching { deleteMessage(chatId = ChatId.fromId(chatId), messageId = messageId) }
    sendMessage(chatId = ChatId.fromId(chatId), text = text, parseMode = ParseMode.HTML)
}
