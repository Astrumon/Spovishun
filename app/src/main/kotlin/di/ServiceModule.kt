package com.ua.astrumon.di

import com.ua.astrumon.domain.bot.cache.ChatCache
import com.ua.astrumon.domain.bot.cache.UserCache
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.domain.bot.service.BirthdayService
import com.ua.astrumon.domain.bot.service.BotMetaService
import com.ua.astrumon.domain.bot.service.ChatService
import com.ua.astrumon.domain.bot.service.GroupService
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.domain.bot.service.ReleaseNotesService
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import java.time.Clock
import java.time.ZoneId

internal val serviceModule = module {
    singleOf(::UserCache)
    singleOf(::ChatCache)
    singleOf(::MemberService)
    singleOf(::GroupService)
    singleOf(::ChatService)
    singleOf(::AutoRegisterService)
    singleOf(::BirthdayService)
    // Explicit lambda: a factory call with a literal argument, not a constructor reference.
    single<Clock> { Clock.system(ZoneId.of("Europe/Kyiv")) }
    singleOf(::ReleaseNotesService)
    singleOf(::BotMetaService)
}
