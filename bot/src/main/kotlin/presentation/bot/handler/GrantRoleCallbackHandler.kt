package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.toText

object GrantRoleCallback {
    const val PREFIX = "grant:"
}

/**
 * `/grantrole` picker: `grant:{memberId}` → role picker built from [MemberRole],
 * `grant:{memberId}:{ROLE}` → assign. The final assignment is admin-guarded in the controller.
 *
 * Shaped like [TwoStepMemberPicker] but not built on it: step 2 selects a role, not a member, so it
 * has neither a [com.ua.astrumon.presentation.controller.PickerListing] source nor a numeric target.
 */
class GrantRoleCallbackHandler(
    private val groupController: GroupController,
) : CallbackHandler {
    override val prefix = GrantRoleCallback.PREFIX

    override suspend fun handle(
        bot: Bot,
        ctx: CallbackContext,
        messages: BotMessages,
    ) {
        val parts = ctx.payload.split(":")
        val memberId = parts.getOrNull(0)?.toLongOrNull() ?: return
        if (parts.size == 1) {
            showRolePicker(bot, ctx, messages, memberId)
            return
        }

        val role = runCatching { MemberRole.valueOf(parts[1]) }.getOrNull() ?: return
        val text = groupController.grantRoleById(ctx.chatId, ctx.clicker.id, memberId, role).toText(
            messages,
            successPrefix = messages.success.prefix,
            onAccessDenied = { messages.error.onlyAdminsRoles },
            onNotFound = { messages.error.resourceNotFound(it.resource, it.identifier) },
        )
        bot.replaceWithText(ctx.chatId, ctx.messageId, text)
    }

    private fun showRolePicker(
        bot: Bot,
        ctx: CallbackContext,
        messages: BotMessages,
        memberId: Long,
    ) {
        val keyboard = InlineKeyboardMarkup.create(
            MemberRole.entries.map { role ->
                listOf(
                    InlineKeyboardButton.CallbackData(
                        text = role.name.lowercase(),
                        callbackData = "${GrantRoleCallback.PREFIX}$memberId:${role.name}",
                    ),
                )
            },
        )
        bot.replaceWithKeyboard(ctx.chatId, ctx.messageId, messages.picker.rolePrompt, keyboard)
    }
}
