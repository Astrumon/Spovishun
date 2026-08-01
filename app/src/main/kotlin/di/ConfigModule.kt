package com.ua.astrumon.di

import com.ua.astrumon.config.AppConfig
import com.ua.astrumon.domain.bot.config.ChatAccessConfig
import com.ua.astrumon.domain.bot.config.ReadinessConfig
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

/** Scope the `/ping` readiness polls run their coalesced re-renders and TTL expiry on (spovishun-119). */
internal object ReadinessScope

/**
 * Dispatcher for work that **blocks** on the Telegram HTTP API rather than computing.
 *
 * `Dispatchers.Default` is sized to the CPU count — one or two threads on the production VM — so a
 * blocking `sendMessage`/`editMessageText` parked there stalls every other coroutine sharing it.
 * Readiness polls edit their message on every vote, which makes that contention real rather than
 * theoretical, so they get an elastic pool instead (spovishun-119).
 */
internal object BlockingTelegramDispatcher

internal val configModule = module {
    single { AppConfig() }
    // Expose AppConfig as the domain-level port the :bot module depends on (keeps :bot off :data).
    single<ChatAccessConfig> { get<AppConfig>() }
    single<ReadinessConfig> { get<AppConfig>() }

    // Scheduler coroutine infrastructure. `onClose` ties each scope to the Koin graph lifecycle:
    // stopKoin() cancels it, so no scheduler outlives the process shutdown (spovishun-155).
    single<CoroutineDispatcher> { Dispatchers.Default }
    // The one deliberate exception to "only DatabaseFactory touches Dispatchers.IO" — see the
    // qualifier's docs. Dispatcher policy is chosen here, in DI; no class hardcodes it.
    single<CoroutineDispatcher>(named<BlockingTelegramDispatcher>()) { Dispatchers.IO }
    single<CoroutineExceptionHandler> { SchedulerExceptionHandler.create() }
    single<CoroutineScope>(named<BirthdaySchedulerScope>()) {
        CoroutineScope(
            SupervisorJob() + get<CoroutineDispatcher>() + get<CoroutineExceptionHandler>() + CoroutineName("birthday-scheduler"),
        )
    }.onClose { it?.cancel() }
    single<CoroutineScope>(named<ReleaseAnnouncerScope>()) {
        CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>() + get<CoroutineExceptionHandler>() + CoroutineName("release-announcer"))
    }.onClose { it?.cancel() }
    single<CoroutineScope>(named<ReadinessScope>()) {
        CoroutineScope(
            SupervisorJob() +
                get<CoroutineDispatcher>(named<BlockingTelegramDispatcher>()) +
                get<CoroutineExceptionHandler>() +
                CoroutineName("readiness"),
        )
    }.onClose { it?.cancel() }
}
