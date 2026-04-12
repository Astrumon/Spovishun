package com.ua.astrumon.common.exception

class ValidationException(
    message: String,
    userMessage: String = message,
    cause: Throwable? = null
) : BaseException(message, cause, "VALIDATION_ERROR", userMessage) {
    override val category = ErrorCategory.VALIDATION
}

class BusinessException(
    message: String,
    userMessage: String = "Business rule violation occurred",
    cause: Throwable? = null
) : BaseException(message, cause, "BUSINESS_ERROR", userMessage) {
    override val category = ErrorCategory.BUSINESS
}

class ResourceNotFoundException(
    val resource: String,
    identifier: String,
    cause: Throwable? = null
) : BaseException(
    "$resource with identifier '$identifier' not found",
    cause,
    "RESOURCE_NOT_FOUND",
    "$resource not found"
) {
    override val category = ErrorCategory.NOT_FOUND
}

class DuplicateResourceException(
    resource: String,
    identifier: String,
    cause: Throwable? = null
) : BaseException(
    "$resource with identifier '$identifier' already exists",
    cause,
    "DUPLICATE_RESOURCE",
    "$resource already exists"
) {
    override val category = ErrorCategory.BUSINESS
}

class DatabaseException(
    message: String,
    cause: Throwable? = null
) : BaseException(
    "Database operation failed: $message",
    cause,
    "DATABASE_ERROR",
    "Data operation failed. Please try again later."
) {
    override val category = ErrorCategory.TECHNICAL
}

class ExternalServiceException(
    service: String,
    message: String,
    cause: Throwable? = null
) : BaseException(
    "External service '$service' error: $message",
    cause,
    "EXTERNAL_SERVICE_ERROR",
    "External service unavailable. Please try again later."
) {
    override val category = ErrorCategory.EXTERNAL
}
