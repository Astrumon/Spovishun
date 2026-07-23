package com.ua.astrumon.presentation.bot.handler

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update

interface CallbackHandler {
    val prefix: String

    suspend fun handle(
        bot: Bot,
        update: Update,
    )
}
