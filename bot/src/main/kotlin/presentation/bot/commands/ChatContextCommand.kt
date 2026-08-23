package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.util.withChatLogContext
import org.slf4j.LoggerFactory

/**
 * Wraps a [BotCommand] so its whole dispatch runs with the originating chat in the log context
 * (spovishun-168).
 *
 * Applied to every command by [com.ua.astrumon.presentation.bot.CommandRegistry], which is the one
 * place the command list is assembled — so a new command is covered the moment it is registered,
 * with nothing to remember. The "invoked" line lives here rather than in the dispatcher so that it,
 * too, carries the chat.
 */
internal class ChatContextCommand(
    private val delegate: BotCommand,
) : BotCommand {
    override val name: String get() = delegate.name

    override val registrationPolicy: RegistrationPolicy get() = delegate.registrationPolicy

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val chat = update.message?.chat
        withChatLogContext(chat) {
            logger.info("Command '{}' invoked", name)
            delegate.execute(bot, update)
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ChatContextCommand::class.java)
    }
}
