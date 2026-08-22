package com.ua.astrumon.presentation.util

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.ua.astrumon.domain.bot.model.MemberRole
import org.slf4j.LoggerFactory

class BotAdminUtils {
    private val logger = LoggerFactory.getLogger(BotAdminUtils::class.java)

    /**
     * Reviewed under spovishun-190 and deliberately left as a broad catch: this is not a `suspend`
     * function and `getChatMember` is a blocking Telegram call, so no `CancellationException` can
     * originate inside the `try` — there is nothing here for a re-throw guard to catch. Role
     * derivation is also best effort by contract; a caller that cannot be confirmed as an admin is
     * treated as a plain member rather than failing their command.
     * Promote this to `suspend` and the guard becomes mandatory.
     */
    fun isUserAdmin(
        bot: Bot,
        chatId: Long,
        userId: Long,
    ): Boolean = try {
        val chatMemberResponse = bot.getChatMember(ChatId.fromId(chatId), userId)
        if (chatMemberResponse.isSuccess) {
            val chatMember = chatMemberResponse.get()
            chatMember.status in listOf("creator", "administrator")
        } else {
            logger.debug("Failed to get chat member status")
            false
        }
    } catch (e: Exception) {
        logger.warn("Error checking admin status: ${e::class.simpleName}")
        false
    }

    fun getMemberRole(
        bot: Bot,
        chatId: Long,
        userId: Long,
    ): MemberRole = if (isUserAdmin(bot, chatId, userId)) {
        MemberRole.ADMIN
    } else {
        MemberRole.MEMBER
    }
}
