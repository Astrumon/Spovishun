package com.ua.astrumon.di

import com.ua.astrumon.config.AppConfig
import com.ua.astrumon.domain.bot.config.ChatAccessConfig
import org.koin.dsl.module

val configModule = module {
    single { AppConfig() }
    // Expose AppConfig as the domain-level port the :bot module depends on (keeps :bot off :data).
    single<ChatAccessConfig> { get<AppConfig>() }
}
