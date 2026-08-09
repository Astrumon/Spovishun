package com.ua.astrumon

import com.ua.astrumon.admin.config.AdminApiConfig
import com.ua.astrumon.admin.server.AdminApiServer
import com.ua.astrumon.config.AppConfig
import com.ua.astrumon.data.db.DatabaseFactory
import com.ua.astrumon.di.appModules
import com.ua.astrumon.presentation.bot.TelegramBot
import com.ua.astrumon.presentation.scheduler.BirthdayGreetingScheduler
import com.ua.astrumon.presentation.scheduler.ReleaseAnnouncer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.slf4j.LoggerFactory

object Application : KoinComponent {
    private val logger = LoggerFactory.getLogger(Application::class.java)
    private val telegramBot: TelegramBot by inject()
    private val config: AppConfig by inject()
    private val birthdayGreetingScheduler: BirthdayGreetingScheduler by inject()
    private val releaseAnnouncer: ReleaseAnnouncer by inject()
    private val adminApiConfig: AdminApiConfig by inject()
    private val adminApiServer: AdminApiServer by inject()

    private val profile = System.getenv("PROFILE") ?: "dev"

    suspend fun run() {
        initializeKoin()
        // Registered only once the graph exists: [shutdown] resolves its dependencies from Koin, so a
        // signal arriving before startKoin() would throw inside the hook and skip the rest of the
        // teardown (spovishun-155).
        Runtime.getRuntime().addShutdownHook(Thread(::shutdown, "shutdown-hook"))
        initializeDatabase()

        val bot = telegramBot.create(config.telegramBotToken)
        if (!telegramBot.verifyIdentity(bot, config.expectedBotUsername)) {
            throw IllegalStateException("Bot identity check failed — refusing to start")
        }
        if (adminApiConfig.enabled) {
            adminApiServer.start()
        }
        birthdayGreetingScheduler.start(bot)
        releaseAnnouncer.notifyIfNewVersion(bot)
        telegramBot.startPolling(bot)
    }

    fun initializeKoin() {
        logger.info("Starting application with profile: {}", profile)

        startKoin {
            modules(appModules)
        }
    }

    /**
     * Closes the object graph on process shutdown (spovishun-155).
     *
     * Ordered outside-in: the admin API stops first (it has its own grace period) so no request is in
     * flight against the Docker client; `stopKoin()` then fires the `onClose` callbacks that cancel the
     * scheduler scopes and release the Docker `HttpClient`; the connection pool goes last.
     *
     * Cancelling a scope is cooperative and does not block, so a query already running on
     * `Dispatchers.IO` may still be unwinding when the pool closes — the cancellation stops new work
     * from starting, it does not drain what is in flight.
     */
    fun shutdown() {
        logger.info("Shutting down application")
        if (adminApiConfig.enabled) adminApiServer.stop()
        stopKoin()
        DatabaseFactory.close()
    }

    private fun initializeDatabase() {
        DatabaseFactory.initialize(config.databaseConfig)
    }
}
