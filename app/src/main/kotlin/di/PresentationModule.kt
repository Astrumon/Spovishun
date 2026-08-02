package com.ua.astrumon.di

import com.ua.astrumon.presentation.bot.BotMessagesProvider
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
import com.ua.astrumon.presentation.bot.commands.RandomCommand
import com.ua.astrumon.presentation.bot.commands.RegisterCommand
import com.ua.astrumon.presentation.bot.commands.RemoveUserFromGroupCommand
import com.ua.astrumon.presentation.bot.commands.ShowGroupsCommand
import com.ua.astrumon.presentation.bot.commands.StartCommand
import com.ua.astrumon.presentation.bot.commands.WhatsNewCommand
import com.ua.astrumon.presentation.bot.handler.AddToGroupCallbackHandler
import com.ua.astrumon.presentation.bot.handler.CallbackHandler
import com.ua.astrumon.presentation.bot.handler.CallbackRouter
import com.ua.astrumon.presentation.bot.handler.DeleteGroupCallbackHandler
import com.ua.astrumon.presentation.bot.handler.GrantRoleCallbackHandler
import com.ua.astrumon.presentation.bot.handler.MessageHandler
import com.ua.astrumon.presentation.bot.handler.PingCallbackHandler
import com.ua.astrumon.presentation.bot.handler.RandomCallbackHandler
import com.ua.astrumon.presentation.bot.handler.ReadinessCallbackHandler
import com.ua.astrumon.presentation.bot.handler.ReadinessSessionRunner
import com.ua.astrumon.presentation.bot.handler.ReadinessSessionStore
import com.ua.astrumon.presentation.bot.handler.RemoveFromGroupCallbackHandler
import com.ua.astrumon.presentation.controller.BirthdayController
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.controller.MembersController
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.controller.RandomController
import com.ua.astrumon.presentation.controller.RegistrationController
import com.ua.astrumon.presentation.controller.WhatsNewController
import com.ua.astrumon.presentation.scheduler.BirthdayGreetingScheduler
import com.ua.astrumon.presentation.scheduler.ReleaseAnnouncer
import com.ua.astrumon.presentation.util.BotAdminUtils
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

internal val presentationModule = module {
    // Localized copy — every controller, command and handler resolves its bundle through this.
    single { BotMessagesProvider(get()) }

    // Controllers
    single { GroupController(get(), get(), get(), get()) }
    single { MembersController(get(), get(), get()) }
    single { RegistrationController(get(), get(), get()) }
    single { PingController(get(), get(), get(), get(), get()) }
    single { BirthdayController(get(), get(), get()) }
    single { WhatsNewController(get(), get(), get(), get()) }
    single { RandomController(get(), get(), get(), get()) }

    // Commands
    single { StartCommand(get(), get(), get()) } bind BotCommand::class
    single { RegisterCommand(get(), get(), get()) } bind BotCommand::class
    single { MembersCommand(get(), get(), get()) } bind BotCommand::class
    single { GrantRoleCommand(get(), get()) } bind BotCommand::class
    single { PingAllCommand(get(), get(), get(), get()) } bind BotCommand::class
    single { PingGroupCommand(get(), get(), get(), get()) } bind BotCommand::class
    single { ShowGroupsCommand(get(), get(), get()) } bind BotCommand::class
    single { NewGroupCommand(get(), get()) } bind BotCommand::class
    single { DeleteGroupCommand(get(), get()) } bind BotCommand::class
    single { AddUserToGroupCommand(get(), get()) } bind BotCommand::class
    single { RemoveUserFromGroupCommand(get(), get()) } bind BotCommand::class
    single { BirthdayCommand(get(), get()) } bind BotCommand::class
    single { WhatsNewCommand(get(), get()) } bind BotCommand::class
    single { RandomCommand(get(), get(), get()) } bind BotCommand::class

    // Schedulers — the qualified scopes they run on are declared in ConfigModule.
    single { BirthdayGreetingScheduler(get(), get(), get(named<BirthdaySchedulerScope>()), get()) }
    single { ReleaseAnnouncer(get(), get(), get(), get(named<ReleaseAnnouncerScope>())) }

    // Bot components
    single { CommandRegistry(getAll()) }

    // Readiness polls — the scope they re-render and expire on is declared in ConfigModule.
    single { ReadinessSessionStore() }
    single { ReadinessSessionRunner(get(), get(named<ReadinessScope>()), get()) }
    single { ReadinessCallbackHandler(get(), get()) } bind CallbackHandler::class

    single { PingCallbackHandler(get(), get(), get(), get()) } bind CallbackHandler::class
    single { DeleteGroupCallbackHandler(get(), get()) } bind CallbackHandler::class
    single { AddToGroupCallbackHandler(get(), get()) } bind CallbackHandler::class
    single { RemoveFromGroupCallbackHandler(get(), get()) } bind CallbackHandler::class
    single { GrantRoleCallbackHandler(get(), get()) } bind CallbackHandler::class
    single { RandomCallbackHandler(get(), get(), get()) } bind CallbackHandler::class
    single { CallbackRouter(getAll()) }
    single { TelegramBot(get(), get(), get(), get()) }
    single { MessageHandler(get(), get(), get()) }
    single { BotAdminUtils() }
}
