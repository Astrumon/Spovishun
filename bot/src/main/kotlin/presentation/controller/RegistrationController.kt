package com.ua.astrumon.presentation.controller

import com.ua.astrumon.domain.bot.model.BirthDate
import com.ua.astrumon.domain.bot.model.MemberRole
import com.ua.astrumon.domain.bot.service.AutoRegisterService
import com.ua.astrumon.domain.bot.service.BirthdayService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.bot.BotMessagesProvider

class RegistrationController(
    private val autoRegisterService: AutoRegisterService,
    private val birthdayService: BirthdayService,
    private val messagesProvider: BotMessagesProvider,
) {
    suspend fun start(request: RegistrationRequest): CommandResponse {
        ensureUserRegistered(request)
        return CommandResponse.Success(messagesProvider.forChat(request.chatId).welcome.message())
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
            resolveRole = { request.userRole },
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
        val messages = messagesProvider.forChat(request.chatId)
        val birthday = birthDateToken?.let {
            BirthDate.parse(it) ?: return CommandResponse.Error(messages.birthday.invalidDate)
        }

        val alreadyRegistered = autoRegisterService.isUserRegistered(request.chatId, request.username).getOrNull()
            ?: return CommandResponse.Error(messages.registration.failed(request.firstName))

        val result = autoRegisterService.ensureUserRegistered(
            chatId = request.chatId,
            userId = request.userId,
            username = request.username,
            firstName = request.firstName,
            resolveRole = { request.userRole },
        )
        if (result.isFailure) {
            return CommandResponse.Error(messages.registration.failed(request.firstName))
        }

        val message = successMessage(messages, alreadyRegistered, request)
        return CommandResponse.Success(
            birthday?.let { withBirthday(messages, request.userId, it, message) } ?: message,
        )
    }

    private fun successMessage(
        messages: BotMessages,
        alreadyRegistered: Boolean,
        request: RegistrationRequest,
    ): String = when {
        alreadyRegistered -> messages.registration.alreadyRegistered(request.firstName)
        request.userRole == MemberRole.ADMIN -> messages.registration.successAdmin(request.firstName)
        else -> messages.registration.success(request.firstName)
    }

    /**
     * Appends the birthday outcome to an already successful registration message.
     * A failed birthday write does not undo the registration, hence a warning suffix, not an error.
     */
    private suspend fun withBirthday(
        messages: BotMessages,
        userId: Long,
        birthday: BirthDate,
        baseMessage: String,
    ): String = birthdayService.setBirthday(userId, birthday).fold(
        onSuccess = { "$baseMessage\n${messages.registration.birthdaySaved(birthday.format())}" },
        onFailure = { "$baseMessage\n${messages.registration.birthdayFailed}" },
    )
}
