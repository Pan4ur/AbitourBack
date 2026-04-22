package dev.pan4ur.abitour.server.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.pan4ur.abitour.server.config.JwtConfig
import dev.pan4ur.abitour.server.model.User
import dev.pan4ur.abitour.server.model.UserRole
import dev.pan4ur.abitour.server.service.AuthService
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import java.util.Date

class JwtTokenService(private val config: JwtConfig) {
    private val algorithm = Algorithm.HMAC256(config.secret)

    fun issueToken(user: User): String {
        val now = System.currentTimeMillis()
        val expiresAt = now + config.accessTokenTtlMinutes * 60_000
        return JWT.create()
            .withAudience(config.audience)
            .withIssuer(config.issuer)
            .withClaim("userId", user.id)
            .withClaim("role", user.role.name)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(expiresAt))
            .sign(algorithm)
    }
}

fun Application.configureSecurity(config: JwtConfig, authService: AuthService) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = config.realm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(config.secret))
                    .withAudience(config.audience)
                    .withIssuer(config.issuer)
                    .build()
            )
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                if (userId.isNullOrBlank()) {
                    null
                } else {
                    runCatching { authService.requireRole(userId) }.getOrNull()?.let {
                        io.ktor.server.auth.jwt.JWTPrincipal(credential.payload)
                    }
                }
            }
        }
    }
}

fun JWTPrincipal.userId(): String = this.payload.getClaim("userId").asString()

fun JWTPrincipal.role(): UserRole = UserRole.valueOf(this.payload.getClaim("role").asString())
