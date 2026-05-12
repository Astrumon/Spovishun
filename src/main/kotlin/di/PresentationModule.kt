package com.ua.astrumon.di

import com.ua.astrumon.presentation.util.BotAdminUtils
import com.ua.astrumon.presentation.bot.CommandRegistry
import com.ua.astrumon.presentation.bot.TelegramBot
import com.ua.astrumon.presentation.bot.commands.AddUserToGroupCommand
import com.ua.astrumon.presentation.bot.commands.BirthdayCommand
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
import com.ua.astrumon.presentation.bot.commands.RandomCommand
import com.ua.astrumon.presentation.bot.commands.WhatsNewCommand
import com.ua.astrumon.presentation.bot.handler.MessageHandler
import com.ua.astrumon.presentation.bot.handler.PingCallbackHandler
import com.ua.astrumon.presentation.controller.BirthdayController
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.controller.MembersController
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.controller.RandomController
import com.ua.astrumon.presentation.controller.RegistrationController
import com.ua.astrumon.presentation.controller.WhatsNewController
import com.ua.astrumon.presentation.scheduler.BirthdayGreetingScheduler
import com.ua.astrumon.presentation.scheduler.ReleaseAnnouncer
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.bind
import org.koin.dsl.module

val presentationModule = module {
    // Controllers
    single { GroupController(get(), get(), get()) }
    single { MembersController(get(), get()) }
    single { RegistrationController(get()) }
    single { PingController(get(), get(), get()) }
    single { BirthdayController(get(), get()) }
    single { WhatsNewController(get(), get(), get()) }
    single { RandomController(get(), get(), get()) }

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
    single { BirthdayCommand(get()) } bind BotCommand::class
    single { WhatsNewCommand(get()) } bind BotCommand::class
    single { RandomCommand(get(), get()) } bind BotCommand::class

    // Schedulers
    single { BirthdayGreetingScheduler(get(), get(), CoroutineScope(SupervisorJob() + CoroutineName("birthday-scheduler"))) }
    single { ReleaseAnnouncer(get(), get(), get(), CoroutineScope(SupervisorJob() + CoroutineName("release-announcer"))) }

    // Bot components
    single { CommandRegistry(getAll()) }
    single { PingCallbackHandler(get(), get()) }
    single { TelegramBot(get(), get(), get(), get()) }
    single { MessageHandler(get(), get(), get()) }
    single { BotAdminUtils() }
}
