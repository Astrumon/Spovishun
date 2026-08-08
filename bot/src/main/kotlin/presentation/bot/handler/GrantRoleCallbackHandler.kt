package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.controller.GroupPickerController
import com.ua.astrumon.presentation.toText

object GrantRoleCallback {
    const val PREFIX = "grant:"
}

/**
 * `/grantrole` picker: `grant:{memberId}` → role picker built from [MemberRole],
 * `grant:{memberId}:{ROLE}` → assign. The final assignment is admin-guarded in the controller.
 *
 * Shaped like [TwoStepMemberPicker] but not built on it: step 2 selects a role, not a member, so it
 * has no [com.ua.astrumon.presentation.controller.PickerListing] source and its selection is a name
 * rather than an id. The payload shape is still shared — see [stepPayload].
 */
class GrantRoleCallbackHandler(
    private val groupPickerController: GroupPickerController,
) : CallbackHandler {
    override val prefix = GrantRoleCallback.PREFIX

    override suspend fun handle(
        bot: Bot,
        ctx: CallbackContext,
        messages: BotMessages,
    ) {
        val payload = ctx.stepPayload() ?: return
        val selectedRole = payload.selection
        if (selectedRole == null) {
            showRolePicker(bot, ctx, messages, payload.ownerId)
            return
        }

        val role = runCatching { MemberRole.valueOf(selectedRole) }.getOrNull() ?: return
        val text = groupPickerController.grantRoleById(ctx.chatId, ctx.clicker.id, payload.ownerId, role).toText(
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
