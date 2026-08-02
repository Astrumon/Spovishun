package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessagesProvider
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
    private val messagesProvider: BotMessagesProvider,
) : CallbackHandler {
    override val prefix = AddToGroupCallback.PREFIX

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
                groupController.chatMembersForModeratorPicker(ctx.chatId, ctx.clickerId),
                PickerCopy(messages, messages.picker.memberPromptAddTo, messages.picker.noMembers, messages.error.onlyAdminsModerators),
            ) { "${AddToGroupCallback.PREFIX}$groupId:${it.id}" }
        } else {
            val memberId = parts[1].toLongOrNull() ?: return
            val text = groupController.addUserToGroupById(ctx.chatId, ctx.clickerId, groupId, memberId).toText(
                messages,
                successPrefix = messages.success.prefix,
                onError = { messages.success.warning(it) },
                onAccessDenied = { messages.error.onlyAdminsModerators },
                onNotFound = { messages.error.resourceNotFound(it.resource, it.identifier) },
            )
            bot.replaceWithText(ctx.chatId, ctx.messageId, text)
        }
    }
}
