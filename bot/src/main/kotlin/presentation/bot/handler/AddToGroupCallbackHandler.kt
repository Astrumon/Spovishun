package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
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

    private val picker = TwoStepMemberPicker(
        prefix = AddToGroupCallback.PREFIX,
        candidates = { ctx, _ -> groupController.chatMembersForModeratorPicker(ctx.chatId, ctx.clicker.id) },
        copy = { messages ->
            PickerCopy(
                messages,
                messages.picker.memberPromptAddTo,
                messages.picker.noMembers,
                messages.error.onlyAdminsModerators,
            )
        },
        act = { ctx, messages, groupId, memberId ->
            groupController.addUserToGroupById(ctx.chatId, ctx.clicker.id, groupId, memberId).toText(
                messages,
                successPrefix = messages.success.prefix,
                onError = { messages.success.warning(it) },
                onAccessDenied = { messages.error.onlyAdminsModerators },
                onNotFound = { messages.error.resourceNotFound(it.resource, it.identifier) },
            )
        },
    )

    override suspend fun handle(
        bot: Bot,
        ctx: CallbackContext,
        messages: BotMessages,
    ) = picker.run(bot, ctx, messages)
}
