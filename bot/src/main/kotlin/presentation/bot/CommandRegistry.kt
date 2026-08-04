package com.ua.astrumon.presentation.bot

import com.ua.astrumon.presentation.bot.commands.BotCommand
import com.ua.astrumon.presentation.bot.commands.ChatContextCommand

class CommandRegistry(
    commands: List<BotCommand>,
) {
    /**
     * Every command, wrapped so its dispatch logs carry the originating chat (spovishun-168).
     * Assembling the list is the single place that sees all commands, so registering one is enough
     * — there is no per-command opt-in to forget.
     */
    val commands: List<BotCommand> = commands.map(::ChatContextCommand)
}
