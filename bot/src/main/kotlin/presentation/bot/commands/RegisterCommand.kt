package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.common.util.sanitizeUsername
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.controller.RegistrationController
import com.ua.astrumon.presentation.controller.RegistrationRequest
import com.ua.astrumon.presentation.toText
import com.ua.astrumon.presentation.util.BotAdminUtils

class RegisterCommand(
    private val registrationController: RegistrationController,
    private val botAdminUtils: BotAdminUtils,
) : BotCommand {
    override val name = "register"

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val user = update.message?.from ?: return
        val (chatId, _, args) = update.messageContext() ?: return

        if (hasBirthdayFlag(args) && args.size != FLAG_WITH_VALUE_SIZE) {
            bot.reply(chatId, BotMessages.Registration.usage)
            return
        }

        val request = RegistrationRequest(
            chatId = chatId,
            userId = user.id,
            username = sanitizeUsername(user.username, user.id),
            firstName = user.firstName,
            userRole = botAdminUtils.getMemberRole(bot, chatId, user.id),
        )
        val response = registrationController.register(request, birthDateToken(args))

        val text = response.toText(BotMessages.Success.prefix)

        bot.reply(chatId, text)
    }

    private fun hasBirthdayFlag(args: List<String>): Boolean {
        val flag = args.firstOrNull() ?: return false
        return flag.equals(BIRTHDAY_FLAG, ignoreCase = true)
    }

    /**
     * Value of the optional `$b DD.MM` flag, or null when the flag is absent.
     * Callers must reject a malformed flag first — see [hasBirthdayFlag].
     */
    private fun birthDateToken(args: List<String>): String? = if (hasBirthdayFlag(args)) args[1] else null

    private companion object {
        const val BIRTHDAY_FLAG = "\$b"

        /** `$b` plus its date argument — anything else is a usage error. */
        const val FLAG_WITH_VALUE_SIZE = 2
    }
}
