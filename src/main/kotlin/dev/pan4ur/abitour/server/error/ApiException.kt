package dev.pan4ur.abitour.server.error

import io.ktor.http.HttpStatusCode

class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String
) : RuntimeException(message)
