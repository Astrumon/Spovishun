package com.ua.astrumon.presentation.bot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Update
import com.ua.astrumon.common.util.sanitizeUsername
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.presentation.controller.RegistrationController
import com.ua.astrumon.presentation.toText
import com.ua.astrumon.presentation.util.BotAdminUtils
import org.slf4j.LoggerFactory

class StartCommand(
    private val registrationController: RegistrationController,
    private val botAdminUtils: BotAdminUtils,
) : BotCommand {

    override val name = "start"

    private val logger = LoggerFactory.getLogger(StartCommand::class.java)

    override suspend fun execute(bot: Bot, update: Update) {
        val chatId = update.message?.chat?.id ?: return
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
                                    chatId = chatId,
                                    userId = admin.user.id,
                                    username = sanitizeUsername(admin.user.username, admin.user.id),
                                    firstName = admin.user.firstName,
                                    userRole = MemberRole.ADMIN
                                )
                            }
                        } else {
                            logger.warn("Failed to get chat administrators")
                        }
                        if (chat.type == "group") {
                            bot.reply(chatId, buildInvitationText())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error processing chat info: ${e::class.simpleName}")
        }

        val userRole = botAdminUtils.getMemberRole(bot, chatId, user.id)
        val username = sanitizeUsername(user.username, user.id)
        val response = registrationController.start(chatId, user.id, username, user.firstName, userRole)

        val text = response.toText()

        bot.reply(chatId, text)
    }

    private fun buildInvitationText(): String = """
        📋 <b>Реєстрація учасників</b>

        Я додав адміністраторів та користувачів з останніх повідомлень до бази даних.

        Якщо вас ще немає в системі, будь ласка, напишіть будь-яке повідомлення (наприклад, <code>/register</code>), щоб я міг вас зареєструвати.

        Це потрібно для того, щоб ви могли користуватися всіма функціями бота.
    """.trimIndent()
}
