package com.ua.astrumon.di

import com.ua.astrumon.admin.config.AdminApiConfig
import com.ua.astrumon.admin.docker.DockerApiClient
import com.ua.astrumon.admin.server.AdminApiServer
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * Koin bindings for the embedded admin API.
 *
 * Named `adminApiDiModule` to keep it distinct from the Ktor routing module
 * [com.ua.astrumon.admin.server.adminApiModule], which is a different concept (spovishun-156).
 */
internal val adminApiDiModule = module {
    // Explicit lambdas: a factory function, then a constructor taking a property off another bean —
    // neither is a plain constructor reference singleOf could take (spovishun-176).
    single { AdminApiConfig.fromEnv() }
    // onClose releases the CIO engine when the graph is torn down (spovishun-155).
    single { DockerApiClient(get<AdminApiConfig>().dockerApiUrl) }.onClose { it?.close() }
    singleOf(::AdminApiServer)
}
