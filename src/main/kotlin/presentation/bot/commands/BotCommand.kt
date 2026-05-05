package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.Update

interface BotCommand {
    val name: String
    suspend fun execute(bot: Bot, update: Update)
}

internal data class MessageContext(val chatId: Long, val userId: Long, val args: List<String>)

internal fun Bot.reply(chatId: Long, text: String) {
    sendMessage(chatId = ChatId.fromId(chatId), text = text, parseMode = ParseMode.HTML)
}

internal fun Bot.replyWithKeyboard(chatId: Long, text: String, keyboard: InlineKeyboardMarkup) {
    sendMessage(chatId = ChatId.fromId(chatId), text = text, parseMode = ParseMode.HTML, replyMarkup = keyboard)
}

internal fun Update.messageContext(): MessageContext? {
    val chatId = message?.chat?.id ?: return null
    val userId = message?.from?.id ?: return null
    val args = message?.text?.split(" ")?.drop(1) ?: emptyList()
    return MessageContext(chatId, userId, args)
}
