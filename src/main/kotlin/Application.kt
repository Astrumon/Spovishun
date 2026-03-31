package com.ua.astrumon

import com.ua.astrumon.config.AppConfig
import com.ua.astrumon.data.db.DatabaseFactory
import com.ua.astrumon.di.configModule
import com.ua.astrumon.di.devRepositoryModule
import com.ua.astrumon.di.presentationModule
import com.ua.astrumon.di.prodRepositoryModule
import com.ua.astrumon.di.serviceModule
import com.ua.astrumon.presentation.bot.TelegramBot
import io.github.cdimascio.dotenv.dotenv
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.slf4j.LoggerFactory

object Application : KoinComponent {
    private val logger = LoggerFactory.getLogger(Application::class.java)
    private val telegramBot: TelegramBot by inject()
    private val config: AppConfig by inject()

    private val env = dotenv { ignoreIfMissing = true }
    private val profile = env["PROFILE"] ?: System.getenv("PROFILE") ?: "dev"
    
    suspend fun run() {
        initializeKoin()
        initializeDatabase()

        val bot = telegramBot.create(config.telegramBotToken)
        telegramBot.startPolling(bot)
    }

    fun initializeKoin() {
        val repositoryModule = when (profile) {
            "prod" -> prodRepositoryModule
            else -> devRepositoryModule
        }

        logger.info("Starting application with profile: {}", profile)
        
        startKoin {
            modules(
                configModule,
                repositoryModule,
                serviceModule,
                presentationModule
            )
        }
    }
    
    private fun initializeDatabase() {
        if (profile == "prod") {
            DatabaseFactory.initialize(config)
        }
    }
}


