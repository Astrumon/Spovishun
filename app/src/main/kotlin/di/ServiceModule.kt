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
import org.koin.dsl.module
import java.time.Clock
import java.time.ZoneId

internal val serviceModule = module {
    single { UserCache() }
    single { ChatCache() }
    single { MemberService(get(), get()) }
    single { GroupService(get(), get()) }
    single { ChatService(get()) }
    single { AutoRegisterService(get(), get(), get(), get()) }
    single { BirthdayService(get(), get(), get()) }
    single<Clock> { Clock.system(ZoneId.of("Europe/Kyiv")) }
    single { ReleaseNotesService(get()) }
    single { BotMetaService(get()) }
}
