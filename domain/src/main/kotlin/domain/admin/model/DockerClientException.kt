package com.ua.astrumon.domain.admin.model

import com.ua.astrumon.common.exception.BaseException
import com.ua.astrumon.common.exception.ErrorCategory

/**
 * Domain-level failure of the read-only Docker Engine API access used by the admin observability API
 * (spovishun-143).
 *
 * Encapsulates HTTP-engine transport failures so the presentation (routing) layer never depends on
 * `io.ktor.*` / `java.nio.*` types. Extends [BaseException] so it fits the project `ResultContainer`
 * failure channel unchanged. The data-layer client maps engine exceptions onto these subtypes; the
 * routing layer maps them onto HTTP status codes.
 */
sealed class DockerClientException(
    message: String,
    cause: Throwable?,
    errorCode: String,
) : BaseException(message, cause, errorCode, "Docker API is temporarily unavailable.") {
    override val category = ErrorCategory.EXTERNAL

    /** The proxy host could not be resolved/connected (DNS failure, refused connection). */
    class Unreachable(
        cause: Throwable? = null,
    ) : DockerClientException("Docker API is unreachable", cause, "DOCKER_UNREACHABLE")

    /** The request exceeded a connect/socket/request timeout. */
    class Timeout(
        cause: Throwable? = null,
    ) : DockerClientException("Docker API request timed out", cause, "DOCKER_TIMEOUT")

    /** The proxy answered with a non-2xx status code. */
    class HttpError(
        val statusCode: Int,
        cause: Throwable? = null,
    ) : DockerClientException("Docker API returned HTTP $statusCode", cause, "DOCKER_HTTP_ERROR")

    /** Any other failure not classified above. */
    class Unknown(
        cause: Throwable? = null,
    ) : DockerClientException("Docker API call failed", cause, "DOCKER_UNKNOWN")
}
