package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.handler.RemoveFromGroupCallback
import com.ua.astrumon.presentation.bot.handler.pickerKeyboard
import com.ua.astrumon.presentation.controller.GroupController
import com.ua.astrumon.presentation.controller.PickerListing
import com.ua.astrumon.presentation.toText

class RemoveUserFromGroupCommand(
    private val groupController: GroupController,
) : BotCommand {
    override val name = "removefromgroup"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val (chatId, userId, args) = update.messageContext() ?: return

        if (args.isEmpty()) {
            showGroupPicker(bot, chatId, userId)
            return
        }

        val text = groupController.removeUserFromGroup(chatId = chatId, userId = userId, args = args).toText(
            successPrefix = BotMessages.Success.prefix,
            onError = { BotMessages.Success.warning(it) },
            onAccessDenied = { BotMessages.Error.onlyAdminsModerators },
            onNotFound = { BotMessages.Error.groupNotFound(it.identifier) },
        )

        bot.reply(chatId, text)
    }

    private suspend fun showGroupPicker(
        bot: Bot,
        chatId: Long,
        userId: Long,
    ) {
        when (val listing = groupController.groupsForModeratorPicker(chatId, userId)) {
            is PickerListing.Reject ->
                bot.reply(chatId, listing.response.toText(onAccessDenied = { BotMessages.Error.onlyAdminsModerators }))

            is PickerListing.Show ->
                if (listing.options.isEmpty()) {
                    bot.reply(chatId, BotMessages.Group.empty)
                } else {
                    bot.replyWithKeyboard(
                        chatId,
                        BotMessages.Picker.groupPromptRemoveFrom,
                        pickerKeyboard(listing.options) { "${RemoveFromGroupCallback.PREFIX}${it.id}" },
                    )
                }
        }
    }
}
