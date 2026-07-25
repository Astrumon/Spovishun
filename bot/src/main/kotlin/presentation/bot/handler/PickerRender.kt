package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.presentation.controller.PickerListing
import com.ua.astrumon.presentation.controller.PickerOption
import com.ua.astrumon.presentation.toText

/** A picker step reduced to what to display: either plain [Text] or a [Keyboard] prompt. */
internal sealed interface PickerRender {
    data class Text(
        val text: String,
    ) : PickerRender

    data class Keyboard(
        val text: String,
        val markup: InlineKeyboardMarkup,
    ) : PickerRender
}

/**
 * Collapses a [PickerListing] into a [PickerRender], centralizing the reject / empty / keyboard decision
 * shared by every picker command and handler. Callers only choose how to deliver it — a fresh reply
 * ([deliver]) or an in-place replace ([deliverInPlace]).
 */
internal fun PickerListing.toRender(
    prompt: String,
    emptyMessage: String,
    accessDeniedMessage: String,
    callbackData: (PickerOption) -> String,
): PickerRender = when (this) {
    is PickerListing.Reject -> PickerRender.Text(response.toText(onAccessDenied = { accessDeniedMessage }))
    is PickerListing.Show ->
        if (options.isEmpty()) {
            PickerRender.Text(emptyMessage)
        } else {
            PickerRender.Keyboard(prompt, pickerKeyboard(options, callbackData))
        }
}

/** Sends a [PickerRender] as a new message — used by the args-less command entry points. */
internal fun Bot.deliver(
    chatId: Long,
    render: PickerRender,
) {
    when (render) {
        is PickerRender.Text ->
            sendMessage(chatId = ChatId.fromId(chatId), text = render.text, parseMode = ParseMode.HTML)

        is PickerRender.Keyboard ->
            sendMessage(chatId = ChatId.fromId(chatId), text = render.text, parseMode = ParseMode.HTML, replyMarkup = render.markup)
    }
}

/** Advances a [PickerRender] in place (delete + send) — used by callback handlers mid-flow. */
internal fun Bot.deliverInPlace(
    chatId: Long,
    messageId: Long,
    render: PickerRender,
) {
    when (render) {
        is PickerRender.Text -> replaceWithText(chatId, messageId, render.text)
        is PickerRender.Keyboard -> replaceWithKeyboard(chatId, messageId, render.text, render.markup)
    }
}
