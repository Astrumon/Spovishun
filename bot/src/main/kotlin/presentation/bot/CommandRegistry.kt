package com.ua.astrumon.presentation.bot

import com.ua.astrumon.presentation.bot.commands.AutoRegisterCommand
import com.ua.astrumon.presentation.bot.commands.BotCommand
import com.ua.astrumon.presentation.bot.commands.ChatContextCommand
import com.ua.astrumon.presentation.util.MemberAutoRegistrar

class CommandRegistry(
    commands: List<BotCommand>,
    autoRegistrar: MemberAutoRegistrar,
) {
    /**
     * Every command, wrapped so its dispatch logs carry the originating chat (spovishun-168) and its
     * caller is registered first (spovishun-172). Assembling the list is the single place that sees
     * all commands, so registering one is enough — there is no per-command opt-in to forget.
     *
     * [ChatContextCommand] goes outermost so the registration itself logs against the chat.
     */
    val commands: List<BotCommand> = commands.map { ChatContextCommand(AutoRegisterCommand(it, autoRegistrar)) }
}
