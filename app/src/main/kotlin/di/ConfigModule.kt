package com.ua.astrumon.di

import com.ua.astrumon.config.AppConfig
import com.ua.astrumon.domain.bot.config.ChatAccessConfig
import com.ua.astrumon.presentation.scheduler.SchedulerExceptionHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose

/** Typed qualifiers for the per-scheduler [CoroutineScope] bindings. */
internal object BirthdaySchedulerScope

internal object ReleaseAnnouncerScope

internal val configModule = module {
    single { AppConfig() }
    // Expose AppConfig as the domain-level port the :bot module depends on (keeps :bot off :data).
    single<ChatAccessConfig> { get<AppConfig>() }

    // Scheduler coroutine infrastructure. `onClose` ties each scope to the Koin graph lifecycle:
    // stopKoin() cancels it, so no scheduler outlives the process shutdown (spovishun-155).
    single<CoroutineDispatcher> { Dispatchers.Default }
    single<CoroutineExceptionHandler> { SchedulerExceptionHandler.create() }
    single<CoroutineScope>(named<BirthdaySchedulerScope>()) {
        CoroutineScope(
            SupervisorJob() + get<CoroutineDispatcher>() + get<CoroutineExceptionHandler>() + CoroutineName("birthday-scheduler"),
        )
    }.onClose { it?.cancel() }
    single<CoroutineScope>(named<ReleaseAnnouncerScope>()) {
        CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>() + get<CoroutineExceptionHandler>() + CoroutineName("release-announcer"))
    }.onClose { it?.cancel() }
}
