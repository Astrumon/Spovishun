package com.ua.astrumon.presentation.util

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.ua.astrumon.domain.model.MemberRole
import org.slf4j.LoggerFactory

class BotAdminUtils {
    private val logger = LoggerFactory.getLogger(BotAdminUtils::class.java)

    fun isUserAdmin(bot: Bot, chatId: Long, userId: Long): Boolean {
        return try {
            val chatMemberResponse = bot.getChatMember(ChatId.fromId(chatId), userId)
            if (chatMemberResponse.isSuccess) {
                val chatMember = chatMemberResponse.get()
                chatMember.status in listOf("creator", "administrator")
            } else {
                logger.debug("Failed to get chat member status for userId: {} in chatId: {}", userId, chatId)
                false
            }
        } catch (e: Exception) {
            logger.warn("Error checking admin status for userId: {} in chatId: {}", userId, chatId, e)
            false
        }
    }

    fun getMemberRole(bot: Bot, chatId: Long, userId: Long): MemberRole = if (isUserAdmin(bot, chatId, userId)) {
        MemberRole.ADMIN
    } else {
        MemberRole.MEMBER
    }
}
