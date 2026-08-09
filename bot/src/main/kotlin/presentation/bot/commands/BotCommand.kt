package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update

/** Who registers the caller of a command — see [BotCommand.registrationPolicy]. */
enum class RegistrationPolicy {
    /** [com.ua.astrumon.presentation.bot.commands.AutoRegisterCommand] registers before dispatch. */
    DISPATCH,

    /** The command registers the caller itself, because its reply depends on the prior state. */
    COMMAND,
}

interface BotCommand {
    val name: String

    /**
     * Defaults to [RegistrationPolicy.DISPATCH], so a new command gets auto-registration without
     * opting in. Override only when the command *reports* on registration and therefore has to see
     * the member table as it was before this update — see [RegisterCommand].
     */
    val registrationPolicy: RegistrationPolicy get() = RegistrationPolicy.DISPATCH

    suspend fun execute(
        bot: Bot,
        update: Update,
    )
}

internal data class MessageContext(
    val chatId: Long,
    val userId: Long,
    val args: List<String>,
)

internal fun Bot.reply(
    chatId: Long,
    text: String,
) {
    sendMessage(chatId = ChatId.fromId(chatId), text = text, parseMode = ParseMode.HTML)
}

internal fun Update.messageContext(): MessageContext? {
    val chatId = message?.chat?.id ?: return null
    val userId = message?.from?.id ?: return null
    val args = message?.text?.split(" ")?.drop(1) ?: emptyList()
    return MessageContext(chatId, userId, args)
}
