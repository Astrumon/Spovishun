package com.ua.astrumon.di

import com.ua.astrumon.domain.cache.ChatCache
import com.ua.astrumon.domain.cache.UserCache
import com.ua.astrumon.domain.service.AutoRegisterService
import com.ua.astrumon.domain.service.BirthdayService
import com.ua.astrumon.domain.service.ChatService
import com.ua.astrumon.domain.service.GroupService
import com.ua.astrumon.domain.service.MemberService
import org.koin.dsl.module
import java.time.Clock
import java.time.ZoneId

val serviceModule = module {
    single { UserCache() }
    single { ChatCache() }
    single { MemberService(get(), get()) }
    single { GroupService(get(), get()) }
    single { ChatService(get()) }
    single { AutoRegisterService(get(), get(), get(), get()) }
    single { BirthdayService(get(), get(), get()) }
    single<Clock> { Clock.system(ZoneId.of("Europe/Kyiv")) }
}
