package com.ua.astrumon.presentation.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.extensions.filters.Filter
import com.ua.astrumon.presentation.bot.handler.MessageHandler
import org.slf4j.LoggerFactory


class TelegramBot(
    private val commandRegistry: CommandRegistry,
    private val messageHandler: MessageHandler,
) {
    private val logger = LoggerFactory.getLogger(TelegramBot::class.java)

    fun create(token: String) = bot {
        this.token = token

        dispatch {
            commandRegistry.commands.forEach { cmd ->
                command(cmd.name) { cmd.execute(bot, update) }
            }

            message(Filter.Text) {
                messageHandler.handleIncomingMessage(bot, update)
            }
        }
    }

    suspend fun startPolling(bot: Bot) {
        try {
            bot.startPolling()
            logger.info("Bot started successfully")
        } catch (e: Exception) {
            logger.error("Failed to start bot", e)
            throw e
        }
    }
}
