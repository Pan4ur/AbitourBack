package dev.pan4ur.abitour.server.routes

import dev.pan4ur.abitour.server.error.ApiException
import dev.pan4ur.abitour.server.model.UserRole
import dev.pan4ur.abitour.server.security.role
import dev.pan4ur.abitour.server.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal

fun requireUserId(call: ApplicationCall): String {
    val principal = call.principal<JWTPrincipal>()
        ?: throw ApiException(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "JWT token is missing")
    return principal.userId()
}

fun requireRole(call: ApplicationCall, expected: UserRole) {
    val principal = call.principal<JWTPrincipal>()
        ?: throw ApiException(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "JWT token is missing")
    if (principal.role() != expected) {
        throw ApiException(HttpStatusCode.Forbidden, "FORBIDDEN", "Insufficient role")
    }
}
