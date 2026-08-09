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
 *
 * [messages] comes first so the default callbacks can read from it — a default-parameter expression
 * may reference earlier parameters. That is what keeps every call site a one-argument change.
 * The parameter count is the documented DSL exception to the 3-parameter rule: four of the five are
 * optional rendering hooks, not inputs.
 */
fun CommandResponse.toText(
    messages: BotMessages,
    successPrefix: String = "",
    onError: (String) -> String = { messages.error.prefixed(it) },
    onAccessDenied: (String) -> String = { messages.error.accessDenied(it) },
    onNotFound: (CommandResponse.NotFound) -> String = { messages.error.notFound },
): String = when (this) {
    is CommandResponse.Success -> "$successPrefix$message"
    is CommandResponse.Error -> onError(message)
    is CommandResponse.AccessDenied -> onAccessDenied(reason)
    is CommandResponse.NotFound -> onNotFound(this)
    is CommandResponse.Silent -> ""
}
