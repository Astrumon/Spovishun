package com.ua.astrumon.presentation.controller

import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages

class RegistrationController(
    private val autoRegisterService: AutoRegisterService,
) {
    suspend fun start(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        userRole: MemberRole,
    ): CommandResponse {
        autoRegisterService.ensureUserRegistered(
            chatId = chatId,
            userId = userId,
            username = username,
            firstName = firstName,
            userRole = userRole,
        )
        return CommandResponse.Success(BotMessages.Welcome.message())
    }

    /**
     * Registers a single user (used by StartCommand for admin sync).
     */
    suspend fun ensureUserRegistered(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        userRole: MemberRole,
    ) {
        autoRegisterService.ensureUserRegistered(
            chatId = chatId,
            userId = userId,
            username = username,
            firstName = firstName,
            userRole = userRole,
        )
    }

    /**
     * Handles /register: registers a single user and returns response.
     */
    suspend fun register(
        chatId: Long,
        userId: Long,
        username: String,
        firstName: String,
        userRole: MemberRole,
    ): CommandResponse {
        val alreadyRegistered = autoRegisterService.isUserRegistered(chatId, username)
        val result = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        if (result.isFailure) {
            return CommandResponse.Error(BotMessages.Registration.failed(firstName))
        }

        return if (alreadyRegistered) {
            CommandResponse.Success(BotMessages.Registration.alreadyRegistered(firstName))
        } else if (userRole == MemberRole.ADMIN) {
            CommandResponse.Success(BotMessages.Registration.successAdmin(firstName))
        } else {
            CommandResponse.Success(BotMessages.Registration.success(firstName))
        }
    }
}
