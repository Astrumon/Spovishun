package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.common.util.sanitizeUsername
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.handler.PickerCopy
import com.ua.astrumon.presentation.bot.handler.PingCallback
import com.ua.astrumon.presentation.bot.handler.ReadinessSessionRunner
import com.ua.astrumon.presentation.bot.handler.sendPicker
import com.ua.astrumon.presentation.controller.PingController
import com.ua.astrumon.presentation.controller.PingOutcome
import com.ua.astrumon.presentation.toText
import com.ua.astrumon.presentation.util.BotAdminUtils

class PingGroupCommand(
    private val pingController: PingController,
    private val botAdminUtils: BotAdminUtils,
    private val readinessSessionRunner: ReadinessSessionRunner,
) : BotCommand {
    override val name = "ping"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val chatId = update.message?.chat?.id ?: return
        val user = update.message?.from ?: return
        val args = update.message
            ?.text
            ?.split(" ")
            ?.drop(1) ?: emptyList()

        val username = sanitizeUsername(user.username, user.id)
        val userRole = botAdminUtils.getMemberRole(bot, chatId, user.id)

        if (args.isEmpty()) {
            val listing = pingController.groupsForPicker(chatId, user.id, username, user.firstName, userRole)
            bot.sendPicker(
                chatId,
                listing,
                PickerCopy(BotMessages.Ping.menuPrompt, BotMessages.Ping.noGroups),
            ) { "${PingCallback.PREFIX}${it.id}" }
            return
        }

        val readinessToggle = ReadinessFlag.parse(args.getOrNull(TOGGLE_FLAG_INDEX))
        if (readinessToggle != null) {
            if (!ReadinessFlag.isWellFormed(args, TOGGLE_FLAG_INDEX)) {
                bot.reply(chatId, BotMessages.Ping.Readiness.usage)
                return
            }
            val response = pingController.setGroupReadiness(chatId, user.id, args[0], readinessToggle)
            bot.reply(chatId, response.toText(BotMessages.Success.prefix, onNotFound = ::groupNotFoundText))
            return
        }

        when (val outcome = pingController.pingGroup(chatId, user.id, username, user.firstName, userRole, args)) {
            is PingOutcome.Plain -> bot.reply(chatId, outcome.response.toText(onNotFound = ::groupNotFoundText))
            is PingOutcome.Readiness -> readinessSessionRunner.start(bot, chatId, outcome.header, outcome.members)
        }
    }

    private fun groupNotFoundText(notFound: CommandResponse.NotFound): String {
        val available = notFound.available.joinToString(", ") { it.escapeHtml() }.ifEmpty { "—" }
        return BotMessages.Error.groupNotFoundHtml(notFound.identifier.escapeHtml(), available)
    }

    private companion object {
        /** `/ping <group> $ready-on` — the toggle sits right after the group key. */
        const val TOGGLE_FLAG_INDEX = 1
    }
}
