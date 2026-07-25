package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.handler.GrantRoleCallback
import com.ua.astrumon.presentation.bot.handler.pickerKeyboard
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.controller.PickerListing
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
        when (val listing = groupController.chatMembersForAdminPicker(chatId, userId)) {
            is PickerListing.Reject ->
                bot.reply(chatId, listing.response.toText(onAccessDenied = { BotMessages.Error.onlyAdminsRoles }))

            is PickerListing.Show ->
                if (listing.options.isEmpty()) {
                    bot.reply(chatId, BotMessages.Picker.noMembers)
                } else {
                    bot.replyWithKeyboard(
                        chatId,
                        BotMessages.Picker.memberPromptGrant,
                        pickerKeyboard(listing.options) { "${GrantRoleCallback.PREFIX}${it.id}" },
                    )
                }
        }
    }
}
