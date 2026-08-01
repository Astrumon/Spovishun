package com.ua.astrumon.presentation.controller

import com.ua.astrumon.domain.bot.model.MemberRole

/**
 * Identity of the member a registration call acts on.
 *
 * Bundles the five values every [RegistrationController] entry point needs so that no method
 * exceeds the 3-parameter limit from the project's Kotlin style rules.
 */
data class RegistrationRequest(
    val chatId: Long,
    val userId: Long,
    val username: String,
    val firstName: String,
    val userRole: MemberRole,
)
