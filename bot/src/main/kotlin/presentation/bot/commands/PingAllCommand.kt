package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.bot.handler.ReadinessSession
import com.ua.astrumon.presentation.bot.handler.ReadinessSessionRunner
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.controller.PingOutcome
import com.ua.astrumon.presentation.toText

class PingAllCommand(
    private val pingController: PingController,
    private val readinessSessionRunner: ReadinessSessionRunner,
    private val messagesProvider: BotMessagesProvider,
) : BotCommand {
    override val name = "all"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val (chatId, userId, args) = update.messageContext() ?: return
        val messages = messagesProvider.forChat(chatId)

        val readinessToggle = ReadinessFlag.parse(args.firstOrNull())
        if (readinessToggle != null) {
            if (!ReadinessFlag.isWellFormed(args, TOGGLE_FLAG_INDEX)) {
                bot.reply(chatId, messages.ping.readiness.usage)
                return
            }
            val response = pingController.setChatReadiness(chatId, userId, readinessToggle)
            bot.reply(chatId, response.toText(messages, messages.success.prefix))
            return
        }

        when (val outcome = pingController.pingAll(chatId, args)) {
            is PingOutcome.Plain -> bot.reply(chatId, outcome.response.toText(messages))
            is PingOutcome.Readiness -> readinessSessionRunner.start(
                bot,
                chatId,
                ReadinessSession(messages, outcome.header, outcome.members),
                initiatorId = userId,
            )
        }
    }

    private companion object {
        /** `/all` takes the toggle as its only argument. */
        const val TOGGLE_FLAG_INDEX = 0
    }
}
