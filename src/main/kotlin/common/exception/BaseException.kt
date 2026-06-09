package com.ua.astrumon.common.exception

abstract class BaseException(
    message: String,
    cause: Throwable? = null,
    val errorCode: String,
    val userMessage: String = message,
) : Exception(message, cause) {
    abstract val category: ErrorCategory

    fun toErrorResponse(): ErrorResponse =
        ErrorResponse(
            code = errorCode,
            message = userMessage,
            category = category.name.lowercase(),
        )
}

enum class ErrorCategory {
    VALIDATION,
    BUSINESS,
    TECHNICAL,
    EXTERNAL,
    AUTHORIZATION,
    NOT_FOUND,
}

data class ErrorResponse(
    val code: String,
    val message: String,
    val category: String,
    val timestamp: Long = System.currentTimeMillis(),
)
