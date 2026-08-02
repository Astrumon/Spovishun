package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.util.UsernameInputSanitizer
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.domain.bot.model.BirthDate
import com.ua.astrumon.domain.bot.service.BirthdayService
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.BotMessagesProvider

class BirthdayController(
    private val birthdayService: BirthdayService,
    memberService: MemberService,
    private val messagesProvider: BotMessagesProvider,
) : BaseController(memberService) {
    // A birthday belongs to a member, not a chat, so the self-service calls take the bundle the
    // command already resolved instead of a chat id they would otherwise ignore.
    suspend fun setOwnBirthday(
        messages: BotMessages,
        userId: Long,
        dateToken: String,
    ): CommandResponse {
        val birthday = BirthDate.parse(dateToken) ?: return CommandResponse.Error(messages.birthday.invalidDate)
        return birthdayService.setBirthday(userId, birthday).fold(
            onSuccess = { CommandResponse.Success(messages.birthday.setSuccess(birthday.format())) },
            onFailure = { ex -> CommandResponse.Error(messages.error.prefixed(ex.userMessage)) },
        )
    }

    suspend fun clearOwnBirthday(
        messages: BotMessages,
        userId: Long,
    ): CommandResponse = birthdayService.clearBirthday(userId).fold(
        onSuccess = { CommandResponse.Success(messages.birthday.cleared) },
        onFailure = { ex -> CommandResponse.Error(messages.error.prefixed(ex.userMessage)) },
    )

    suspend fun setBirthdayForOther(
        chatId: Long,
        userId: Long,
        args: List<String>,
    ): CommandResponse {
        val messages = messagesProvider.forChat(chatId)
        requireModeratorAccess(chatId, userId)?.let { return it }

        if (args.size < 2) return CommandResponse.Error(messages.birthday.usage)

        val birthday = BirthDate.parse(args[0]) ?: return CommandResponse.Error(messages.birthday.invalidDate)

        val parsed = UsernameInputSanitizer.parseUsernames(args[1])
        val username = parsed.valid.firstOrNull()
            ?: return CommandResponse.Error(messages.birthday.usage)

        return birthdayService.setBirthdayForUsername(username, birthday).fold(
            onSuccess = { CommandResponse.Success(messages.birthday.setSuccess(birthday.format())) },
            onFailure = { ex ->
                if (ex is ResourceNotFoundException) {
                    CommandResponse.Error(messages.birthday.userNotRegistered(username.escapeHtml()))
                } else {
                    CommandResponse.Error(messages.error.prefixed(ex.userMessage))
                }
            },
        )
    }
}
