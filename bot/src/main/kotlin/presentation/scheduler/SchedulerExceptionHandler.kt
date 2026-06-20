package com.ua.astrumon.presentation.scheduler

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Scope-level [CoroutineExceptionHandler] for background schedulers.
 *
 * A [kotlinx.coroutines.SupervisorJob] only isolates sibling failures; an uncaught throw still
 * reaches the JVM default handler and vanishes with no log or alert. Installing this handler in
 * every scheduler scope guarantees such failures are surfaced (and can be forwarded to
 * observability) instead of silently killing the feature.
 */
object SchedulerExceptionHandler {
    private const val UNKNOWN_COROUTINE = "unknown"

    fun create(logger: Logger = LoggerFactory.getLogger(SchedulerExceptionHandler::class.java)): CoroutineExceptionHandler =
        CoroutineExceptionHandler { context, throwable ->
            val coroutineName = context[CoroutineName]?.name ?: UNKNOWN_COROUTINE
            logger.error("Uncaught exception in scheduler coroutine [{}]", coroutineName, throwable)
            // Observability hook: forward to error tracking here once an alerting sink exists.
        }
}
