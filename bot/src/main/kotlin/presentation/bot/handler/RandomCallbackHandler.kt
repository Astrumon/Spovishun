package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.controller.RandomController
import com.ua.astrumon.presentation.toText

object RandomCallback {
    const val PREFIX = "random:"
}

/**
 * `/random` picker: `random:{groupId}` picks inside that group, `random:0`
 * ([RandomController.ALL_MEMBERS_ID]) picks across the whole chat.
 */
class RandomCallbackHandler(
    private val randomController: RandomController,
) : CallbackHandler {
    override val prefix = RandomCallback.PREFIX

    override suspend fun handle(
        bot: Bot,
        ctx: CallbackContext,
        messages: BotMessages,
    ) {
        val groupId = ctx.payload.toLongOrNull() ?: return

        val response = if (groupId == RandomController.ALL_MEMBERS_ID) {
            randomController.pickRandomAll(ctx.chatId)
        } else {
            randomController.pickRandomFromGroupById(ctx.chatId, groupId)
        }

        val text = response.toText(
            messages,
            onNotFound = { messages.error.groupNotFoundHtml(it.identifier.escapeHtml(), "—") },
        )
        bot.replaceWithText(ctx.chatId, ctx.messageId, text)
    }
}
