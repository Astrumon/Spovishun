package com.ua.astrumon.presentation

sealed class CommandResponse {
    data class Success(val message: String) : CommandResponse()
    data class AccessDenied(val reason: String) : CommandResponse()
    data class NotFound(val resource: String, val identifier: String, val available: List<String> = emptyList()) : CommandResponse()
    data class Error(val message: String) : CommandResponse()
}

/**
 * Universal rendering for all commands.
 * Provide callbacks only for the cases that differ from defaults.
 *
 * Defaults: Success → message, Error → "❌ msg", AccessDenied → "🚫 reason", NotFound → "❌ Помилка."
 */
fun CommandResponse.toText(
    successPrefix: String = "",
    onError: (String) -> String = { "❌ $it" },
    onAccessDenied: (String) -> String = { "🚫 $it" },
    onNotFound: (CommandResponse.NotFound) -> String = { "❌ Помилка." },
): String = when (this) {
    is CommandResponse.Success -> "$successPrefix$message"
    is CommandResponse.Error -> onError(message)
    is CommandResponse.AccessDenied -> onAccessDenied(reason)
    is CommandResponse.NotFound -> onNotFound(this)
}
