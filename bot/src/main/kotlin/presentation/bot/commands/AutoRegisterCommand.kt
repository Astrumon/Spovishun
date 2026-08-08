package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.util.MemberAutoRegistrar

/**
 * Wraps a [BotCommand] so the caller is registered before the command runs (spovishun-172).
 *
 * Applied to every command by [com.ua.astrumon.presentation.bot.CommandRegistry], the same single
 * assembly point [ChatContextCommand] uses — registration is therefore a property of being a
 * command, not eleven `ensureUserRegistered` lines spread across four controllers.
 */
internal class AutoRegisterCommand(
    private val delegate: BotCommand,
    private val autoRegistrar: MemberAutoRegistrar,
) : BotCommand {
    override val name: String get() = delegate.name

    override val registrationPolicy: RegistrationPolicy get() = delegate.registrationPolicy

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val message = update.message
        val user = message?.from
        // Registering ahead of a command that reports on registration would make it always answer
        // "already registered" — the caller would have been created a line earlier.
        if (message != null && user != null && delegate.registrationPolicy == RegistrationPolicy.DISPATCH) {
            autoRegistrar.ensure(bot, message.chat, user)
        }
        delegate.execute(bot, update)
    }
}
