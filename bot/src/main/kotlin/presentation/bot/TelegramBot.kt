package com.ua.astrumon.presentation.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.extensions.filters.Filter
import com.ua.astrumon.domain.bot.config.ChatAccessConfig
import com.ua.astrumon.presentation.bot.handler.CallbackRouter
import com.ua.astrumon.presentation.bot.handler.MessageHandler
import org.slf4j.LoggerFactory

class TelegramBot(
    private val commandRegistry: CommandRegistry,
    private val messageHandler: MessageHandler,
    private val config: ChatAccessConfig,
    private val callbackRouter: CallbackRouter,
) {
    private val logger = LoggerFactory.getLogger(TelegramBot::class.java)

    fun create(token: String) = bot {
        this.token = token

        dispatch {
            commandRegistry.commands.forEach { cmd ->
                command(cmd.name) {
                    val chatId = update.message?.chat?.id
                    if (config.allowedChatIds.isNotEmpty() && chatId !in config.allowedChatIds) return@command
                    // The "invoked" line moved into ChatContextCommand, which wraps every registry
                    // entry — logged there it carries the originating chat (spovishun-168).
                    cmd.execute(bot, update)
                }
            }

            message(Filter.Text) {
                messageHandler.handleIncomingMessage(bot, update)
            }

            callbackQuery {
                val chatId = update.callbackQuery
                    ?.message
                    ?.chat
                    ?.id
                if (config.allowedChatIds.isNotEmpty() && chatId !in config.allowedChatIds) return@callbackQuery
                callbackRouter.route(bot, update)
            }
        }
    }

    fun verifyIdentity(
        bot: Bot,
        expectedUsername: String?,
    ): Boolean {
        if (expectedUsername.isNullOrBlank()) {
            logger.warn("EXPECTED_BOT_USERNAME not set — identity check skipped")
            return true
        }
        val result = bot.getMe()
        val actual = result.fold({ it.username }, { null })
        return (actual == expectedUsername).also {
            if (!it) logger.error("Bot identity mismatch — refusing to start")
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
