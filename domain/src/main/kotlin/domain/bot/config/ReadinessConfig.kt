package com.ua.astrumon.domain.bot.config

import kotlin.time.Duration

/**
 * Timing contract for the `/ping` readiness poll (spovishun-119).
 * Declared in :domain as a port; the app layer ([AppConfig]) implements it so the :bot module
 * never depends on :app, mirroring [ChatAccessConfig].
 */
interface ReadinessConfig {
    /** How long a readiness poll accepts votes before it is finalized and its keyboard removed. */
    val readinessTtl: Duration
}
