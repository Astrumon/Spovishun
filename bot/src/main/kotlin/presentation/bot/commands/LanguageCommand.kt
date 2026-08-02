package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.domain.bot.model.BotLanguage
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.bot.handler.LanguageCallback
import com.ua.astrumon.presentation.bot.handler.PickerCopy
import com.ua.astrumon.presentation.bot.handler.sendPicker
import com.ua.astrumon.presentation.controller.LanguageController

/** `/language` — shows the UA/EN picker. Takes no arguments; the choice is made on the keyboard. */
class LanguageCommand(
    private val languageController: LanguageController,
    private val messagesProvider: BotMessagesProvider,
) : BotCommand {
    override val name = "language"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val (chatId, userId, _) = update.messageContext() ?: return
        val messages = messagesProvider.forChat(chatId)

        bot.sendPicker(
            chatId,
            languageController.languageOptions(chatId, userId),
            PickerCopy(
                messages,
                messages.languageSetting.prompt,
                // The listing is built from BotLanguage.entries, so it is never empty.
                messages.languageSetting.prompt,
                messages.error.onlyAdminsModerators,
            ),
        ) { option -> LanguageCallback.PREFIX + BotLanguage.entries[option.id.toInt()].code }
    }
}
