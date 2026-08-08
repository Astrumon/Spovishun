package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.bot.handler.GrantRoleCallback
import com.ua.astrumon.presentation.bot.handler.PickerCopy
import com.ua.astrumon.presentation.bot.handler.sendPicker
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.controller.GroupPickerController
import com.ua.astrumon.presentation.toText

class GrantRoleCommand(
    private val groupController: GroupController,
    private val groupPickerController: GroupPickerController,
    private val messagesProvider: BotMessagesProvider,
) : BotCommand {
    override val name = "grantrole"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val (chatId, userId, args) = update.messageContext() ?: return
        val messages = messagesProvider.forChat(chatId)

        if (args.isEmpty()) {
            bot.sendPicker(
                chatId,
                groupPickerController.chatMembersForAdminPicker(chatId, userId),
                PickerCopy(messages, messages.picker.memberPromptGrant, messages.picker.noMembers, messages.error.onlyAdminsRoles),
            ) { "${GrantRoleCallback.PREFIX}${it.id}" }
            return
        }

        val text = groupController.grantRole(chatId = chatId, userId = userId, args = args).toText(
            messages,
            successPrefix = messages.success.prefix,
            onAccessDenied = { messages.error.onlyAdminsRoles },
            onNotFound = { messages.error.resourceNotFound(it.resource, it.identifier) },
        )

        bot.reply(chatId, text)
    }
}
