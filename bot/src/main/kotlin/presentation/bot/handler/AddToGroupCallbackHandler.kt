package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.toText

object AddToGroupCallback {
    const val PREFIX = "addto:"
}

/**
 * `/addtogroup` picker: `addto:{groupId}` → chat-member picker, `addto:{groupId}:{memberId}` → add.
 * Step-1 selection lives in the callback data of step-2 buttons — no server-side state.
 */
class AddToGroupCallbackHandler(
    private val groupController: GroupController,
) : CallbackHandler {
    override val prefix = AddToGroupCallback.PREFIX

    override suspend fun handle(
        bot: Bot,
        update: Update,
    ) {
        val callbackQuery = update.callbackQuery ?: return
        bot.answerCallbackQuery(callbackQuery.id)
        val ctx = update.callbackContext(prefix) ?: return

        val parts = ctx.payload.split(":")
        val groupId = parts.getOrNull(0)?.toLongOrNull() ?: return
        if (parts.size == 1) {
            showMemberPicker(bot, ctx, groupId)
        } else {
            val memberId = parts[1].toLongOrNull() ?: return
            val text = groupController.addUserToGroupById(ctx.chatId, ctx.clickerId, groupId, memberId).toText(
                successPrefix = BotMessages.Success.prefix,
                onError = { BotMessages.Success.warning(it) },
                onAccessDenied = { BotMessages.Error.onlyAdminsModerators },
                onNotFound = { BotMessages.Error.resourceNotFound(it.resource, it.identifier) },
            )
            bot.replaceWithText(ctx.chatId, ctx.messageId, text)
        }
    }

    private suspend fun showMemberPicker(
        bot: Bot,
        ctx: CallbackContext,
        groupId: Long,
    ) {
        val render = groupController.chatMembersForModeratorPicker(ctx.chatId, ctx.clickerId).toRender(
            prompt = BotMessages.Picker.memberPromptAddTo,
            emptyMessage = BotMessages.Picker.noMembers,
            accessDeniedMessage = BotMessages.Error.onlyAdminsModerators,
            callbackData = { "${AddToGroupCallback.PREFIX}$groupId:${it.id}" },
        )
        bot.deliverInPlace(ctx.chatId, ctx.messageId, render)
    }
}
