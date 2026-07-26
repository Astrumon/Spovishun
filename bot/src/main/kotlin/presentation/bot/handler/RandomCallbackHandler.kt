package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.common.util.sanitizeUsername
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.controller.RandomController
import com.ua.astrumon.presentation.toText
import com.ua.astrumon.presentation.util.BotAdminUtils

object RandomCallback {
    const val PREFIX = "random:"
}

/**
 * `/random` picker: `random:{groupId}` picks inside that group, `random:0`
 * ([RandomController.ALL_MEMBERS_ID]) picks across the whole chat.
 */
class RandomCallbackHandler(
    private val randomController: RandomController,
    private val botAdminUtils: BotAdminUtils,
) : CallbackHandler {
    override val prefix = RandomCallback.PREFIX

    override suspend fun handle(
        bot: Bot,
        update: Update,
    ) {
        val callbackQuery = update.callbackQuery ?: return
        bot.answerCallbackQuery(callbackQuery.id)
        val ctx = update.callbackContext(prefix) ?: return
        val groupId = ctx.payload.toLongOrNull() ?: return

        val user = callbackQuery.from
        val username = sanitizeUsername(user.username, user.id)
        val userRole = botAdminUtils.getMemberRole(bot, ctx.chatId, user.id)

        val response = if (groupId == RandomController.ALL_MEMBERS_ID) {
            randomController.pickRandomAll(ctx.chatId, ctx.clickerId, username, user.firstName, userRole)
        } else {
            randomController.pickRandomFromGroupById(ctx.chatId, ctx.clickerId, username, user.firstName, userRole, groupId)
        }

        val text = response.toText(
            onNotFound = { BotMessages.Error.groupNotFoundHtml(it.identifier.escapeHtml(), "—") },
        )
        bot.replaceWithText(ctx.chatId, ctx.messageId, text)
    }
}
