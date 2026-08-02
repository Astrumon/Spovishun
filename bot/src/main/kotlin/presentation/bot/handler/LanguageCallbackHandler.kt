package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.domain.bot.model.BotLanguage
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.controller.LanguageController
import com.ua.astrumon.presentation.toText

object LanguageCallback {
    const val PREFIX = "lang:"
}

/**
 * `/language` picker: `lang:{code}` persists the choice and closes the picker (spovishun-152).
 *
 * The reply is rendered from the **newly selected** language, so the confirmation itself is the
 * first proof the switch took effect.
 */
class LanguageCallbackHandler(
    private val languageController: LanguageController,
    private val messagesProvider: BotMessagesProvider,
) : CallbackHandler {
    override val prefix = LanguageCallback.PREFIX

    override suspend fun handle(
        bot: Bot,
        update: Update,
    ) {
        val callbackQuery = update.callbackQuery ?: return
        bot.answerCallbackQuery(callbackQuery.id)
        val ctx = update.callbackContext(prefix) ?: return

        val selected = BotLanguage.fromCode(ctx.payload)
        val response = languageController.setLanguage(ctx.chatId, ctx.clickerId, selected)
        val messages = messagesProvider.forChat(ctx.chatId)

        val text = response.toText(
            messages,
            successPrefix = messages.success.prefix,
            onAccessDenied = { messages.error.onlyAdminsModerators },
        )
        bot.replaceWithText(ctx.chatId, ctx.messageId, text)
    }
}
