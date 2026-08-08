package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.toText

object DeleteGroupCallback {
    const val PREFIX = "delgroup:"
    const val CONFIRM = "confirm:"
    const val CANCEL = "cancel"
}

/**
 * `/delgroup` picker: `delgroup:{id}` → confirmation screen, `delgroup:confirm:{id}` → delete,
 * `delgroup:cancel` → abort. The destructive delete never runs without the confirm step.
 */
class DeleteGroupCallbackHandler(
    private val groupController: GroupController,
) : CallbackHandler {
    override val prefix = DeleteGroupCallback.PREFIX

    override suspend fun handle(
        bot: Bot,
        ctx: CallbackContext,
        messages: BotMessages,
    ) {
        when {
            ctx.payload == DeleteGroupCallback.CANCEL ->
                bot.replaceWithText(ctx.chatId, ctx.messageId, messages.picker.cancelled)

            ctx.payload.startsWith(DeleteGroupCallback.CONFIRM) ->
                confirmDelete(bot, ctx, messages, ctx.payload.removePrefix(DeleteGroupCallback.CONFIRM))

            else -> showConfirm(bot, ctx, messages, ctx.payload)
        }
    }

    private fun showConfirm(
        bot: Bot,
        ctx: CallbackContext,
        messages: BotMessages,
        rawGroupId: String,
    ) {
        val groupId = rawGroupId.toLongOrNull() ?: return
        val keyboard = InlineKeyboardMarkup.create(
            listOf(
                listOf(
                    InlineKeyboardButton.CallbackData(
                        text = messages.picker.confirmButton,
                        callbackData = "${DeleteGroupCallback.PREFIX}${DeleteGroupCallback.CONFIRM}$groupId",
                    ),
                    InlineKeyboardButton.CallbackData(
                        text = messages.picker.cancelButton,
                        callbackData = "${DeleteGroupCallback.PREFIX}${DeleteGroupCallback.CANCEL}",
                    ),
                ),
            ),
        )
        bot.replaceWithKeyboard(ctx.chatId, ctx.messageId, messages.picker.confirmPrompt, keyboard)
    }

    private suspend fun confirmDelete(
        bot: Bot,
        ctx: CallbackContext,
        messages: BotMessages,
        rawGroupId: String,
    ) {
        val groupId = rawGroupId.toLongOrNull() ?: return
        val text = groupController.deleteGroupById(ctx.chatId, ctx.clicker.id, groupId).toText(
            messages,
            successPrefix = messages.success.deletePrefix,
            onAccessDenied = { messages.error.onlyAdminsModerators },
            onNotFound = { messages.error.groupNotFound(it.identifier) },
        )
        bot.replaceWithText(ctx.chatId, ctx.messageId, text)
    }
}
