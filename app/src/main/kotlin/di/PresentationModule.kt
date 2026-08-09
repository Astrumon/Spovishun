package com.ua.astrumon.di

import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.bot.CommandRegistry
import com.ua.astrumon.presentation.bot.TelegramBot
import com.ua.astrumon.presentation.bot.commands.AddUserToGroupCommand
import com.ua.astrumon.presentation.bot.commands.BirthdayCommand
import com.ua.astrumon.presentation.bot.commands.BotCommand
import com.ua.astrumon.presentation.bot.commands.DeleteGroupCommand
import com.ua.astrumon.presentation.bot.commands.EditGroupCommand
import com.ua.astrumon.presentation.bot.commands.GrantRoleCommand
import com.ua.astrumon.presentation.bot.commands.LanguageCommand
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
import com.ua.astrumon.presentation.bot.handler.LanguageCallbackHandler
import com.ua.astrumon.presentation.bot.handler.MessageHandler
import com.ua.astrumon.presentation.bot.handler.PingCallbackHandler
import com.ua.astrumon.presentation.bot.handler.RandomCallbackHandler
import com.ua.astrumon.presentation.bot.handler.ReadinessCallbackHandler
import com.ua.astrumon.presentation.bot.handler.ReadinessSessionRunner
import com.ua.astrumon.presentation.bot.handler.ReadinessSessionStore
import com.ua.astrumon.presentation.bot.handler.RemoveFromGroupCallbackHandler
import com.ua.astrumon.presentation.controller.BirthdayController
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.controller.GroupPickerController
import com.ua.astrumon.presentation.controller.GroupSettingsController
import com.ua.astrumon.presentation.controller.LanguageController
import com.ua.astrumon.presentation.controller.MembersController
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.controller.RandomController
import com.ua.astrumon.presentation.controller.RegistrationController
import com.ua.astrumon.presentation.controller.WhatsNewController
import com.ua.astrumon.presentation.scheduler.BirthdayGreetingScheduler
import com.ua.astrumon.presentation.scheduler.ReleaseAnnouncer
import com.ua.astrumon.presentation.util.BotAdminUtils
import com.ua.astrumon.presentation.util.MemberAutoRegistrar
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Bindings use the constructor DSL (`singleOf(::X)`), which resolves parameters by type rather than
 * by position (spovishun-176). A chain of untyped `get()` is only kept where `singleOf` cannot
 * express the resolution — a parameter qualifier or a `getAll()` collection — and each such binding
 * says why below.
 */
internal val presentationModule = module {
    // Localized copy — every controller, command and handler resolves its bundle through this.
    singleOf(::BotMessagesProvider)

    // Controllers
    singleOf(::GroupController)
    singleOf(::GroupPickerController)
    singleOf(::GroupSettingsController)
    singleOf(::MembersController)
    singleOf(::RegistrationController)
    singleOf(::PingController)
    singleOf(::BirthdayController)
    singleOf(::WhatsNewController)
    singleOf(::RandomController)
    singleOf(::LanguageController)

    // Commands
    singleOf(::StartCommand) bind BotCommand::class
    singleOf(::RegisterCommand) bind BotCommand::class
    singleOf(::MembersCommand) bind BotCommand::class
    singleOf(::GrantRoleCommand) bind BotCommand::class
    singleOf(::PingAllCommand) bind BotCommand::class
    singleOf(::PingGroupCommand) bind BotCommand::class
    singleOf(::ShowGroupsCommand) bind BotCommand::class
    singleOf(::NewGroupCommand) bind BotCommand::class
    singleOf(::DeleteGroupCommand) bind BotCommand::class
    singleOf(::EditGroupCommand) bind BotCommand::class
    singleOf(::AddUserToGroupCommand) bind BotCommand::class
    singleOf(::RemoveUserFromGroupCommand) bind BotCommand::class
    singleOf(::BirthdayCommand) bind BotCommand::class
    singleOf(::WhatsNewCommand) bind BotCommand::class
    singleOf(::RandomCommand) bind BotCommand::class
    singleOf(::LanguageCommand) bind BotCommand::class

    // Schedulers — explicit lambdas: each takes a qualified CoroutineScope declared in ConfigModule,
    // and singleOf cannot express a parameter qualifier.
    single { BirthdayGreetingScheduler(get(), get(), get(named<BirthdaySchedulerScope>()), get()) }
    single { ReleaseAnnouncer(get(), get(), get(), get(named<ReleaseAnnouncerScope>())) }

    // Bot components — explicit lambda: getAll() is collection injection, not a constructor resolve.
    single { CommandRegistry(getAll(), get()) }

    // Readiness polls — the runner stays explicit for the same reason as the schedulers: the scope it
    // re-renders and expires on is qualified.
    singleOf(::ReadinessSessionStore)
    single { ReadinessSessionRunner(get(), get(named<ReadinessScope>()), get()) }
    singleOf(::ReadinessCallbackHandler) bind CallbackHandler::class

    singleOf(::PingCallbackHandler) bind CallbackHandler::class
    singleOf(::DeleteGroupCallbackHandler) bind CallbackHandler::class
    singleOf(::AddToGroupCallbackHandler) bind CallbackHandler::class
    singleOf(::RemoveFromGroupCallbackHandler) bind CallbackHandler::class
    singleOf(::GrantRoleCallbackHandler) bind CallbackHandler::class
    singleOf(::RandomCallbackHandler) bind CallbackHandler::class
    singleOf(::LanguageCallbackHandler) bind CallbackHandler::class
    // Explicit lambda: getAll() collects every CallbackHandler bound above.
    single { CallbackRouter(getAll(), get(), get()) }
    singleOf(::TelegramBot)
    singleOf(::MessageHandler)
    singleOf(::BotAdminUtils)
    singleOf(::MemberAutoRegistrar)
}
