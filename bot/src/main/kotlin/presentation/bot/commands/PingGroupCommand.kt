package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.bot.handler.PickerCopy
import com.ua.astrumon.presentation.bot.handler.PingCallback
import com.ua.astrumon.presentation.bot.handler.ReadinessSession
import com.ua.astrumon.presentation.bot.handler.ReadinessSessionRunner
import com.ua.astrumon.presentation.bot.handler.sendPicker
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.controller.PingOutcome
import com.ua.astrumon.presentation.toText

class PingGroupCommand(
    private val pingController: PingController,
    private val readinessSessionRunner: ReadinessSessionRunner,
    private val messagesProvider: BotMessagesProvider,
) : BotCommand {
    override val name = "ping"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val (chatId, userId, args) = update.messageContext() ?: return
        val messages = messagesProvider.forChat(chatId)

        if (args.isEmpty()) {
            sendGroupPicker(bot, chatId, messages)
            return
        }

        val readinessToggle = ReadinessFlag.parse(args.getOrNull(TOGGLE_FLAG_INDEX))
        if (readinessToggle != null) {
            if (!ReadinessFlag.isWellFormed(args, TOGGLE_FLAG_INDEX)) {
                bot.reply(chatId, messages.ping.readiness.usage)
                return
            }
            val response = pingController.setGroupReadiness(chatId, userId, args[0], readinessToggle)
            bot.reply(chatId, response.toText(messages, messages.success.prefix, onNotFound = { groupNotFoundText(messages, it) }))
            return
        }

        when (val outcome = pingController.pingGroup(chatId, args)) {
            is PingOutcome.Plain -> bot.reply(chatId, outcome.response.toText(messages, onNotFound = { groupNotFoundText(messages, it) }))
            is PingOutcome.Readiness -> readinessSessionRunner.start(
                bot,
                chatId,
                ReadinessSession(messages, outcome.header, outcome.members),
                initiatorId = userId,
            )
        }
    }

    /** `/ping` with no arguments asks which group — the answer arrives as a `ping:{groupId}` tap. */
    private suspend fun sendGroupPicker(
        bot: Bot,
        chatId: Long,
        messages: BotMessages,
    ) {
        bot.sendPicker(
            chatId,
            pingController.groupsForPicker(chatId),
            PickerCopy(messages, messages.ping.menuPrompt, messages.ping.noGroups),
        ) { "${PingCallback.PREFIX}${it.id}" }
    }

    private fun groupNotFoundText(
        messages: BotMessages,
        notFound: CommandResponse.NotFound,
    ): String {
        val available = notFound.available.joinToString(", ") { it.escapeHtml() }.ifEmpty { "—" }
        return messages.error.groupNotFoundHtml(notFound.identifier.escapeHtml(), available)
    }

    private companion object {
        /** `/ping <group> $ready-on` — the toggle sits right after the group key. */
        const val TOGGLE_FLAG_INDEX = 1
    }
}
