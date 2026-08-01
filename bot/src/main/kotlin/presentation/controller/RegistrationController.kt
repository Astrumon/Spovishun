package com.ua.astrumon.presentation.controller

import com.ua.astrumon.domain.bot.model.BirthDate
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.domain.bot.service.BirthdayService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages

class RegistrationController(
    private val autoRegisterService: AutoRegisterService,
    private val birthdayService: BirthdayService,
) {
    suspend fun start(request: RegistrationRequest): CommandResponse {
        ensureUserRegistered(request)
        return CommandResponse.Success(BotMessages.Welcome.message())
    }

    /**
     * Registers a single user (used by StartCommand for admin sync).
     */
    suspend fun ensureUserRegistered(request: RegistrationRequest) {
        autoRegisterService.ensureUserRegistered(
            chatId = request.chatId,
            userId = request.userId,
            username = request.username,
            firstName = request.firstName,
            userRole = request.userRole,
        )
    }

    /**
     * Handles /register: registers a single user and returns response.
     *
     * [birthDateToken] carries the optional `$b DD.MM` flag value. It is validated before any
     * database write, so an invalid date leaves the user unregistered.
     */
    suspend fun register(
        request: RegistrationRequest,
        birthDateToken: String? = null,
    ): CommandResponse {
        val birthday = birthDateToken?.let {
            BirthDate.parse(it) ?: return CommandResponse.Error(BotMessages.Birthday.invalidDate)
        }

        val alreadyRegistered = autoRegisterService.isUserRegistered(request.chatId, request.username).getOrNull()
            ?: return CommandResponse.Error(BotMessages.Registration.failed(request.firstName))

        val result = autoRegisterService.ensureUserRegistered(
            chatId = request.chatId,
            userId = request.userId,
            username = request.username,
            firstName = request.firstName,
            userRole = request.userRole,
        )
        if (result.isFailure) {
            return CommandResponse.Error(BotMessages.Registration.failed(request.firstName))
        }

        val message = successMessage(alreadyRegistered, request)
        return CommandResponse.Success(
            birthday?.let { withBirthday(request.userId, it, message) } ?: message,
        )
    }

    private fun successMessage(
        alreadyRegistered: Boolean,
        request: RegistrationRequest,
    ): String = when {
        alreadyRegistered -> BotMessages.Registration.alreadyRegistered(request.firstName)
        request.userRole == MemberRole.ADMIN -> BotMessages.Registration.successAdmin(request.firstName)
        else -> BotMessages.Registration.success(request.firstName)
    }

    /**
     * Appends the birthday outcome to an already successful registration message.
     * A failed birthday write does not undo the registration, hence a warning suffix, not an error.
     */
    private suspend fun withBirthday(
        userId: Long,
        birthday: BirthDate,
        baseMessage: String,
    ): String = birthdayService.setBirthday(userId, birthday).fold(
        onSuccess = { "$baseMessage\n${BotMessages.Registration.birthdaySaved(birthday.format())}" },
        onFailure = { "$baseMessage\n${BotMessages.Registration.birthdayFailed}" },
    )
}
