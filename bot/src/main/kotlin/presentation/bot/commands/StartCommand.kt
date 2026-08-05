package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.common.util.UsernameInputSanitizer
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.presentation.bot.BotMessagesProvider
import com.ua.astrumon.presentation.controller.RegistrationController
import com.ua.astrumon.presentation.controller.RegistrationRequest
import com.ua.astrumon.presentation.toText
import com.ua.astrumon.presentation.util.BotAdminUtils
import org.slf4j.LoggerFactory

class StartCommand(
    private val registrationController: RegistrationController,
    private val botAdminUtils: BotAdminUtils,
    private val messagesProvider: BotMessagesProvider,
) : BotCommand {
    override val name = "start"

    private val logger = LoggerFactory.getLogger(StartCommand::class.java)

    override suspend fun execute(
        bot: Bot,
        update: Update,
    ) {
        val chatId = update.message?.chat?.id ?: return
        val messages = messagesProvider.forChat(chatId)
        val user = update.message?.from ?: return

        try {
            val chatResponse = bot.getChat(ChatId.fromId(chatId))
            if (chatResponse.isSuccess && chatResponse.getOrNull() != null) {
                val chat = chatResponse.get()
                when (chat.type) {
                    "group", "supergroup" -> {
                        val adminsResponse = bot.getChatAdministrators(ChatId.fromId(chatId))
                        if (adminsResponse.isSuccess && adminsResponse.getOrNull() != null) {
                            adminsResponse.get().forEach { admin ->
                                registrationController.ensureUserRegistered(
                                    RegistrationRequest(
                                        chatId = chatId,
                                        userId = admin.user.id,
                                        username = UsernameInputSanitizer.sanitizeUsername(admin.user.username, admin.user.id),
                                        firstName = admin.user.firstName,
                                        userRole = MemberRole.ADMIN,
                                    ),
                                )
                            }
                        } else {
                            logger.warn("Failed to get chat administrators")
                        }
                        if (chat.type == "group") {
                            bot.reply(chatId, messages.welcome.invitation)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error processing chat info: ${e::class.simpleName}")
        }

        val request = RegistrationRequest(
            chatId = chatId,
            userId = user.id,
            username = UsernameInputSanitizer.sanitizeUsername(user.username, user.id),
            firstName = user.firstName,
            userRole = botAdminUtils.getMemberRole(bot, chatId, user.id),
        )
        val response = registrationController.start(request)

        val text = response.toText(messages)

        bot.reply(chatId, text)
    }
}
