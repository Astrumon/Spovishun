package com.ua.astrumon.common.exception

class NotFoundException(
    message: String,
    cause: Throwable? = null,
    errorCode: String = "NOT_FOUND",
    userMessage: String = message,
) : BaseException(message, cause, errorCode, userMessage) {
    override val category = ErrorCategory.NOT_FOUND
}
