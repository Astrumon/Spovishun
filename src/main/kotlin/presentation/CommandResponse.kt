package com.ua.astrumon.presentation

import com.ua.astrumon.presentation.bot.BotMessages

sealed class CommandResponse {
    data class Success(
        val message: String,
    ) : CommandResponse()

    data class AccessDenied(
        val reason: String,
    ) : CommandResponse()

    data class NotFound(
        val resource: String,
        val identifier: String,
        val available: List<String> = emptyList(),
    ) : CommandResponse()

    data class Error(
        val message: String,
    ) : CommandResponse()

    data object Silent : CommandResponse()
}

/**
 * Universal rendering for all commands.
 * Provide callbacks only for the cases that differ from defaults.
 */
fun CommandResponse.toText(
    successPrefix: String = "",
    onError: (String) -> String = { BotMessages.Error.prefixed(it) },
    onAccessDenied: (String) -> String = { BotMessages.Error.accessDenied(it) },
    onNotFound: (CommandResponse.NotFound) -> String = { BotMessages.Error.notFound },
): String = when (this) {
    is CommandResponse.Success -> "$successPrefix$message"
    is CommandResponse.Error -> onError(message)
    is CommandResponse.AccessDenied -> onAccessDenied(reason)
    is CommandResponse.NotFound -> onNotFound(this)
    is CommandResponse.Silent -> ""
}
