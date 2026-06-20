package com.ua.astrumon.di

import com.ua.astrumon.admin.config.AdminApiConfig
import com.ua.astrumon.admin.docker.DockerApiClient
import com.ua.astrumon.admin.server.AdminApiServer
import org.koin.dsl.module

val adminApiModule = module {
    single { AdminApiConfig.fromEnv() }
    single { DockerApiClient(get<AdminApiConfig>().dockerApiUrl) }
    single { AdminApiServer(get(), get(), get()) }
}
