package com.ua.astrumon.di

import com.ua.astrumon.presentation.util.BotAdminUtils
import com.ua.astrumon.presentation.bot.CommandRegistry
import com.ua.astrumon.presentation.bot.TelegramBot
import com.ua.astrumon.presentation.bot.commands.AddUserToGroupCommand
import com.ua.astrumon.presentation.bot.commands.BotCommand
import com.ua.astrumon.presentation.bot.commands.DeleteGroupCommand
import com.ua.astrumon.presentation.bot.commands.GrantRoleCommand
import com.ua.astrumon.presentation.bot.commands.MembersCommand
import com.ua.astrumon.presentation.bot.commands.NewGroupCommand
import com.ua.astrumon.presentation.bot.commands.PingAllCommand
import com.ua.astrumon.presentation.bot.commands.PingGroupCommand
import com.ua.astrumon.presentation.bot.commands.RegisterCommand
import com.ua.astrumon.presentation.bot.commands.RemoveUserFromGroupCommand
import com.ua.astrumon.presentation.bot.commands.ShowGroupsCommand
import com.ua.astrumon.presentation.bot.commands.StartCommand
import com.ua.astrumon.presentation.bot.handler.MessageHandler
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.controller.MembersController
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.controller.RegistrationController
import org.koin.dsl.bind
import org.koin.dsl.module

val presentationModule = module {
    // Controllers
    single { GroupController(get(), get(), get()) }
    single { MembersController(get(), get()) }
    single { RegistrationController(get(), get()) }
    single { PingController(get(), get(), get()) }

    // Commands
    single { StartCommand(get(), get()) } bind BotCommand::class
    single { RegisterCommand(get(), get()) } bind BotCommand::class
    single { MembersCommand(get(), get()) } bind BotCommand::class
    single { GrantRoleCommand(get()) } bind BotCommand::class
    single { PingAllCommand(get(), get()) } bind BotCommand::class
    single { PingGroupCommand(get(), get()) } bind BotCommand::class
    single { ShowGroupsCommand(get(), get()) } bind BotCommand::class
    single { NewGroupCommand(get()) } bind BotCommand::class
    single { DeleteGroupCommand(get()) } bind BotCommand::class
    single { AddUserToGroupCommand(get()) } bind BotCommand::class
    single { RemoveUserFromGroupCommand(get()) } bind BotCommand::class

    // Bot components
    single { CommandRegistry(getAll()) }
    single { TelegramBot(get(), get()) }
    single { MessageHandler(get(), get()) }
    single { BotAdminUtils() }
}
