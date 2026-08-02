package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessagesProvider
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
    private val messagesProvider: BotMessagesProvider,
) : CallbackHandler {
    override val prefix = RemoveFromGroupCallback.PREFIX

    override suspend fun handle(
        bot: Bot,
        update: Update,
    ) {
        val callbackQuery = update.callbackQuery ?: return
        bot.answerCallbackQuery(callbackQuery.id)
        val ctx = update.callbackContext(prefix) ?: return
        val messages = messagesProvider.forChat(ctx.chatId)

        val parts = ctx.payload.split(":")
        val groupId = parts.getOrNull(0)?.toLongOrNull() ?: return
        if (parts.size == 1) {
            bot.advancePicker(
                ctx.chatId,
                ctx.messageId,
                groupController.groupMembersForPicker(ctx.chatId, ctx.clickerId, groupId),
                PickerCopy(
                    messages,
                    messages.picker.memberPromptRemoveFrom,
                    messages.picker.noMembers,
                    messages.error.onlyAdminsModerators,
                ),
            ) { "${RemoveFromGroupCallback.PREFIX}$groupId:${it.id}" }
        } else {
            val memberId = parts[1].toLongOrNull() ?: return
            val text = groupController.removeUserFromGroupById(ctx.chatId, ctx.clickerId, groupId, memberId).toText(
                messages,
                successPrefix = messages.success.prefix,
                onError = { messages.success.warning(it) },
                onAccessDenied = { messages.error.onlyAdminsModerators },
                onNotFound = { messages.error.groupNotFound(it.identifier) },
            )
            bot.replaceWithText(ctx.chatId, ctx.messageId, text)
        }
    }
}
