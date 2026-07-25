package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.toText

object RemoveFromGroupCallback {
    const val PREFIX = "removefrom:"
}

/**
 * `/removefromgroup` picker: `removefrom:{groupId}` → picker of that group's members,
 * `removefrom:{groupId}:{memberId}` → remove. The second step is scoped to the chosen group.
 */
class RemoveFromGroupCallbackHandler(
    private val groupController: GroupController,
) : CallbackHandler {
    override val prefix = RemoveFromGroupCallback.PREFIX

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
            val text = groupController.removeUserFromGroupById(ctx.chatId, ctx.clickerId, groupId, memberId).toText(
                successPrefix = BotMessages.Success.prefix,
                onError = { BotMessages.Success.warning(it) },
                onAccessDenied = { BotMessages.Error.onlyAdminsModerators },
                onNotFound = { BotMessages.Error.groupNotFound(it.identifier) },
            )
            bot.replaceWithText(ctx.chatId, ctx.messageId, text)
        }
    }

    private suspend fun showMemberPicker(
        bot: Bot,
        ctx: CallbackContext,
        groupId: Long,
    ) {
        val render = groupController.groupMembersForPicker(ctx.chatId, ctx.clickerId, groupId).toRender(
            prompt = BotMessages.Picker.memberPromptRemoveFrom,
            emptyMessage = BotMessages.Picker.noMembers,
            accessDeniedMessage = BotMessages.Error.onlyAdminsModerators,
            callbackData = { "${RemoveFromGroupCallback.PREFIX}$groupId:${it.id}" },
        )
        bot.deliverInPlace(ctx.chatId, ctx.messageId, render)
    }
}
