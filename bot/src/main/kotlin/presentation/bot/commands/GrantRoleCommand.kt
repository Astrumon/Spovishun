package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.handler.GrantRoleCallback
import com.ua.astrumon.presentation.bot.handler.deliver
import com.ua.astrumon.presentation.bot.handler.toRender
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.toText

class GrantRoleCommand(
    private val groupController: GroupController,
) : BotCommand {
    override val name = "grantrole"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val (chatId, userId, args) = update.messageContext() ?: return

        if (args.isEmpty()) {
            showMemberPicker(bot, chatId, userId)
            return
        }

        val text = groupController.grantRole(chatId = chatId, userId = userId, args = args).toText(
            successPrefix = BotMessages.Success.prefix,
            onAccessDenied = { BotMessages.Error.onlyAdminsRoles },
            onNotFound = { BotMessages.Error.resourceNotFound(it.resource, it.identifier) },
        )

        bot.reply(chatId, text)
    }

    private suspend fun showMemberPicker(
        bot: Bot,
        chatId: Long,
        userId: Long,
    ) {
        val render = groupController.chatMembersForAdminPicker(chatId, userId).toRender(
            prompt = BotMessages.Picker.memberPromptGrant,
            emptyMessage = BotMessages.Picker.noMembers,
            accessDeniedMessage = BotMessages.Error.onlyAdminsRoles,
            callbackData = { "${GrantRoleCallback.PREFIX}${it.id}" },
        )
        bot.deliver(chatId, render)
    }
}
