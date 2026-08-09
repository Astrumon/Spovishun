package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.ua.astrumon.domain.bot.model.BotLanguage
import com.ua.astrumon.presentation.bot.BotMessages
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
 * first proof the switch took effect — which is why this handler re-resolves its copy instead of
 * using the bundle the router passed in.
 */
class LanguageCallbackHandler(
    private val languageController: LanguageController,
    private val messagesProvider: BotMessagesProvider,
) : CallbackHandler {
    override val prefix = LanguageCallback.PREFIX

    override suspend fun handle(
        bot: Bot,
        ctx: CallbackContext,
        messages: BotMessages,
    ) {
        val selected = BotLanguage.fromCode(ctx.payload)
        val response = languageController.setLanguage(ctx.chatId, ctx.clicker.id, selected)
        val updated = messagesProvider.forChat(ctx.chatId)

        val text = response.toText(
            updated,
            successPrefix = updated.success.prefix,
            onAccessDenied = { updated.error.onlyAdminsModerators },
        )
        bot.replaceWithText(ctx.chatId, ctx.messageId, text)
    }
}
