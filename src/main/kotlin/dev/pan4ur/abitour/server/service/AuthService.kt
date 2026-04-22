package dev.pan4ur.abitour.server.service

import dev.pan4ur.abitour.server.error.ApiException
import dev.pan4ur.abitour.server.model.User
import dev.pan4ur.abitour.server.model.UserRole
import dev.pan4ur.abitour.server.repository.BackendRepository
import dev.pan4ur.abitour.server.security.hash
import dev.pan4ur.abitour.server.security.verify
import io.ktor.http.HttpStatusCode

class AuthService(
    private val repository: BackendRepository
) {
    fun register(name: String, roleRaw: String, password: String): User {
        val normalizedName = validateName(name)
        val role = validateRole(roleRaw)
        validatePassword(password)
        if (repository.findAccount(normalizedName) != null) {
            throw ApiException(HttpStatusCode.Conflict, "USER_EXISTS", "User already exists")
        }
        val passwordHash = hash(password)
        return repository.createUser(normalizedName, role, passwordHash)
    }

    fun login(name: String, password: String): User {
        val normalizedName = validateName(name)
        validatePassword(password)
        val account = repository.findAccount(normalizedName)
            ?: throw ApiException(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS", "Invalid credentials")
        if (!verify(password, account.passwordHash)) {
            throw ApiException(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS", "Invalid credentials")
        }
        return User(id = account.id, name = account.name, role = account.role)
    }

    fun getUser(userId: String): User {
        return repository.findUser(userId)
            ?: throw ApiException(HttpStatusCode.Unauthorized, "UNKNOWN_USER", "User is not registered")
    }

    fun requireRole(userId: String): UserRole {
        return getUser(userId).role
    }

    fun getUserName(userId: String): String {
        return repository.findUserName(userId) ?: "Unknown"
    }

    fun listUsers(): List<User> {
        return repository.listUsers()
    }

    private fun validateName(name: String): String {
        val normalized = name.trim()
        if (normalized.isBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_NAME", "Name must not be blank")
        }
        if (normalized.length < 2) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_NAME", "Name is too short")
        }
        return normalized
    }

    private fun validateRole(roleRaw: String): UserRole {
        return runCatching { UserRole.valueOf(roleRaw.uppercase()) }
            .getOrElse {
                throw ApiException(
                    HttpStatusCode.BadRequest,
                    "INVALID_ROLE",
                    "Role must be APPLICANT or TEACHER"
                )
            }
    }

    private fun validatePassword(password: String) {
        if (password.length < 6) {
            throw ApiException(
                HttpStatusCode.BadRequest,
                "INVALID_PASSWORD",
                "Password must be at least 6 characters"
            )
        }
    }
}
