package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.controller.PickerListing
import com.ua.astrumon.presentation.controller.PickerOption
import com.ua.astrumon.presentation.toText

/**
 * UI copy for a single picker step: the [prompt] plus its empty and access-denied fallbacks.
 * A null [accessDeniedMessage] keeps the generic `toText()` wording — for pickers with no role gate.
 *
 * [messages] is the bundle the other three strings were rendered from. Carrying it here is what lets
 * the generic fallback stay in the chat's language without widening `sendPicker` / `advancePicker`.
 */
internal data class PickerCopy(
    val messages: BotMessages,
    val prompt: String,
    val emptyMessage: String,
    val accessDeniedMessage: String? = null,
)

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
 * Renders a picker listing as a **new** reply — the shared entry point for every args-less command.
 * Centralizes the reject / empty / keyboard decision so commands supply only [copy] and [callbackData].
 */
internal fun Bot.sendPicker(
    chatId: Long,
    listing: PickerListing,
    copy: PickerCopy,
    callbackData: (PickerOption) -> String,
) = deliver(chatId, listing.toRender(copy, callbackData))

/**
 * Advances a picker listing **in place** (delete + send) — the shared mid-flow step for callback
 * handlers. Same decision logic as [sendPicker], different delivery.
 */
internal fun Bot.advancePicker(
    chatId: Long,
    messageId: Long,
    listing: PickerListing,
    copy: PickerCopy,
    callbackData: (PickerOption) -> String,
) = deliverInPlace(chatId, messageId, listing.toRender(copy, callbackData))

private fun PickerListing.toRender(
    copy: PickerCopy,
    callbackData: (PickerOption) -> String,
): PickerRender = when (this) {
    is PickerListing.Reject ->
        PickerRender.Text(
            response.toText(copy.messages, onAccessDenied = { reason ->
                copy.accessDeniedMessage ?: copy.messages.error.accessDenied(reason)
            }),
        )
    is PickerListing.Show ->
        if (options.isEmpty()) {
            PickerRender.Text(copy.emptyMessage)
        } else {
            PickerRender.Keyboard(copy.prompt, pickerKeyboard(options, callbackData))
        }
}

private fun Bot.deliver(
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

private fun Bot.deliverInPlace(
    chatId: Long,
    messageId: Long,
    render: PickerRender,
) {
    when (render) {
        is PickerRender.Text -> replaceWithText(chatId, messageId, render.text)
        is PickerRender.Keyboard -> replaceWithKeyboard(chatId, messageId, render.text, render.markup)
    }
}
