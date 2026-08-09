package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.controller.GroupPickerController
import com.ua.astrumon.presentation.toText

object RemoveFromGroupCallback {
    const val PREFIX = "removefrom:"
}

/**
 * `/removefromgroup` picker: `removefrom:{groupId}` → picker of that group's members,
 * `removefrom:{groupId}:{memberId}` → remove. The second step is scoped to the chosen group.
 */
class RemoveFromGroupCallbackHandler(
    private val groupPickerController: GroupPickerController,
) : CallbackHandler {
    override val prefix = RemoveFromGroupCallback.PREFIX

    private val picker = TwoStepMemberPicker(
        prefix = RemoveFromGroupCallback.PREFIX,
        candidates = { ctx, groupId -> groupPickerController.groupMembersForPicker(ctx.chatId, ctx.clicker.id, groupId) },
        copy = { messages ->
            PickerCopy(
                messages,
                messages.picker.memberPromptRemoveFrom,
                messages.picker.noMembers,
                messages.error.onlyAdminsModerators,
            )
        },
        act = { ctx, messages, groupId, memberId ->
            groupPickerController.removeUserFromGroupById(ctx.chatId, ctx.clicker.id, groupId, memberId).toText(
                messages,
                successPrefix = messages.success.prefix,
                onError = { messages.success.warning(it) },
                onAccessDenied = { messages.error.onlyAdminsModerators },
                onNotFound = { messages.error.groupNotFound(it.identifier) },
            )
        },
    )

    override suspend fun handle(
        bot: Bot,
        ctx: CallbackContext,
        messages: BotMessages,
    ) = picker.run(bot, ctx, messages)
}
