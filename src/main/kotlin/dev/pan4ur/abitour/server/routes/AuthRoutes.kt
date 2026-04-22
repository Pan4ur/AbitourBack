package dev.pan4ur.abitour.server.routes

import dev.pan4ur.abitour.server.dto.RegisterRequest
import dev.pan4ur.abitour.server.dto.LoginRequest
import dev.pan4ur.abitour.server.dto.LoginResponse
import dev.pan4ur.abitour.server.dto.UserDto
import dev.pan4ur.abitour.server.security.userId
import dev.pan4ur.abitour.server.security.JwtTokenService
import dev.pan4ur.abitour.server.service.AuthService
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes(authService: AuthService, tokenService: JwtTokenService) {
    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            val user = authService.register(request.name, request.role, request.password)
            val token = tokenService.issueToken(user)
            call.respond(
                LoginResponse(
                    token = token,
                    user = UserDto(
                        id = user.id,
                        name = user.name,
                        role = user.role.name
                    )
                )
            )
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val user = authService.login(request.name, request.password)
            val token = tokenService.issueToken(user)
            call.respond(
                LoginResponse(
                    token = token,
                    user = UserDto(
                        id = user.id,
                        name = user.name,
                        role = user.role.name
                    )
                )
            )
        }

        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>() ?: error("Missing principal")
                val user = authService.getUser(principal.userId())
                call.respond(
                    UserDto(
                        id = user.id,
                        name = user.name,
                        role = user.role.name
                    )
                )
            }
        }
    }
}
