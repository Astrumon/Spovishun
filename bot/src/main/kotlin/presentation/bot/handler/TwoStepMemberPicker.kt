package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.controller.PickerListing

/**
 * The `{ownerId}` → member-list → `{ownerId}:{memberId}` → act flow, which `/addtogroup` and
 * `/removefromgroup` ran as two structurally identical copies (spovishun-172).
 *
 * A handler now states only what differs: where the candidates come from, how the step-2 prompt
 * reads, and what tapping a member does. Delivery reuses [Bot.advancePicker] and [Bot.replaceWithText].
 */
internal class TwoStepMemberPicker(
    private val prefix: String,
    private val candidates: suspend (CallbackContext, Long) -> PickerListing,
    private val copy: (BotMessages) -> PickerCopy,
    private val act: suspend (CallbackContext, BotMessages, Long, Long) -> String,
) {
    suspend fun run(
        bot: Bot,
        ctx: CallbackContext,
        messages: BotMessages,
    ) {
        val payload = ctx.twoStepPayload() ?: return
        val targetId = payload.targetId
        if (targetId == null) {
            bot.advancePicker(
                ctx.chatId,
                ctx.messageId,
                candidates(ctx, payload.ownerId),
                copy(messages),
            ) { "$prefix${payload.ownerId}:${it.id}" }
            return
        }

        bot.replaceWithText(ctx.chatId, ctx.messageId, act(ctx, messages, payload.ownerId, targetId))
    }
}
