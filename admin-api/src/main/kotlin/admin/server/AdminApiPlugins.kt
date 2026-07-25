package com.ua.astrumon.admin.server

import com.ua.astrumon.admin.auth.TokenAuthenticator
import com.ua.astrumon.common.exception.BaseException
import com.ua.astrumon.domain.admin.model.DockerClientException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

/**
 * Ktor plugin installation for the admin observability API, kept separate from the route definitions
 * in [adminApiModule] (spovishun-143): error mapping, bearer auth, and the domain-error → HTTP status
 * translation shared by routing and the [StatusPages] safety net.
 */

internal const val AUTH_PROVIDER = "admin"

private val logger = LoggerFactory.getLogger("AdminApiPlugins")

// Safety net: maps a domain client error that escaped routing to its HTTP status, and any other
// uncaught throwable to 500. Routing folds results explicitly, so this rarely fires.
internal fun Application.installErrorMapping() {
    install(StatusPages) {
        exception<DockerClientException> { call, cause ->
            logger.warn("Docker client error reached StatusPages safety net", cause)
            call.respond(cause.toHttpStatus())
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled admin API error", cause)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}

internal fun Application.installBearerAuth(token: String) {
    install(Authentication) {
        bearer(AUTH_PROVIDER) {
            authenticate { credential ->
                if (TokenAuthenticator.matches(credential.token, token)) UserIdPrincipal(AUTH_PROVIDER) else null
            }
        }
    }
}

// Unreachable proxy -> 503, timeout -> 504, upstream non-2xx -> that code, everything else -> 500.
internal fun BaseException.toHttpStatus(): HttpStatusCode = when (this) {
    is DockerClientException.Unreachable -> HttpStatusCode.ServiceUnavailable
    is DockerClientException.Timeout -> HttpStatusCode.GatewayTimeout
    is DockerClientException.HttpError -> HttpStatusCode.fromValue(statusCode)
    else -> HttpStatusCode.InternalServerError
}
