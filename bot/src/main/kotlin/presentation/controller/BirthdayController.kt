package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.util.UsernameInputSanitizer
import com.ua.astrumon.common.util.escapeHtml
import com.ua.astrumon.domain.bot.model.BirthDate
import com.ua.astrumon.domain.bot.service.BirthdayService
import com.ua.astrumon.domain.bot.service.MemberService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages

class BirthdayController(
    private val birthdayService: BirthdayService,
    memberService: MemberService,
) : BaseController(memberService) {
    suspend fun setOwnBirthday(
        userId: Long,
        dateToken: String,
    ): CommandResponse {
        val birthday = BirthDate.parse(dateToken) ?: return CommandResponse.Error(BotMessages.Birthday.invalidDate)
        return birthdayService.setBirthday(userId, birthday).fold(
            onSuccess = { CommandResponse.Success(BotMessages.Birthday.setSuccess(birthday.format())) },
            onFailure = { ex -> CommandResponse.Error(BotMessages.Error.prefixed(ex.userMessage)) },
        )
    }

    suspend fun clearOwnBirthday(userId: Long): CommandResponse = birthdayService.clearBirthday(userId).fold(
        onSuccess = { CommandResponse.Success(BotMessages.Birthday.cleared) },
        onFailure = { ex -> CommandResponse.Error(BotMessages.Error.prefixed(ex.userMessage)) },
    )

    suspend fun setBirthdayForOther(
        chatId: Long,
        userId: Long,
        args: List<String>,
    ): CommandResponse {
        requireModeratorAccess(chatId, userId)?.let { return it }

        if (args.size < 2) return CommandResponse.Error(BotMessages.Birthday.usage)

        val birthday = BirthDate.parse(args[0]) ?: return CommandResponse.Error(BotMessages.Birthday.invalidDate)

        val parsed = UsernameInputSanitizer.parseUsernames(args[1])
        val username = parsed.valid.firstOrNull()
            ?: return CommandResponse.Error(BotMessages.Birthday.usage)

        return birthdayService.setBirthdayForUsername(username, birthday).fold(
            onSuccess = { CommandResponse.Success(BotMessages.Birthday.setSuccess(birthday.format())) },
            onFailure = { ex ->
                if (ex is ResourceNotFoundException) {
                    CommandResponse.Error(BotMessages.Birthday.userNotRegistered(username.escapeHtml()))
                } else {
                    CommandResponse.Error(BotMessages.Error.prefixed(ex.userMessage))
                }
            },
        )
    }
}
