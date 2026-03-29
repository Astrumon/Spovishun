package com.ua.astrumon.presentation

sealed class CommandResponse {
    data class Success(val message: String) : CommandResponse()
    data class AccessDenied(val reason: String) : CommandResponse()
    data class NotFound(val resource: String, val identifier: String, val available: List<String> = emptyList()) : CommandResponse()
    data class Error(val message: String) : CommandResponse()
}

/**
 * Default rendering for commands that follow the standard pattern:
 * Success → [successPrefix] + message, Error → "❌ msg", AccessDenied → "🚫 reason", NotFound → "❌ Помилка."
 *
 * Commands with custom NotFound/AccessDenied text use explicit `when` instead.
 */
fun CommandResponse.toText(
    successPrefix: String = "",
    onError: (String) -> String = { "❌ $it" },
): String = when (this) {
    is CommandResponse.Success -> "$successPrefix$message"
    is CommandResponse.Error -> onError(message)
    is CommandResponse.AccessDenied -> "🚫 $reason"
    is CommandResponse.NotFound -> "❌ Помилка."
}
