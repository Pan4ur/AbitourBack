package dev.pan4ur.abitour.server

import dev.pan4ur.abitour.server.config.AppConfig
import dev.pan4ur.abitour.server.db.DatabaseFactory
import dev.pan4ur.abitour.server.http.configureHttp
import dev.pan4ur.abitour.server.http.configureSerialization
import dev.pan4ur.abitour.server.http.configureStatusPages
import dev.pan4ur.abitour.server.repository.RepositoryFactory
import dev.pan4ur.abitour.server.routes.registerRoutes
import dev.pan4ur.abitour.server.security.configureSecurity
import dev.pan4ur.abitour.server.security.JwtTokenService
import dev.pan4ur.abitour.server.service.AuthService
import dev.pan4ur.abitour.server.service.ProgressService
import dev.pan4ur.abitour.server.service.QuestService
import dev.pan4ur.abitour.server.service.RecommendationService
import dev.pan4ur.abitour.server.service.TeacherService
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callloging.CallLogging
import org.slf4j.event.Level

fun Application.module() {
    val config = AppConfig.from(environment.config)
    DatabaseFactory.init(config.database)
    val repository = RepositoryFactory.create()
    val tokenService = JwtTokenService(config.jwt)

    val authService = AuthService(repository)
    val questService = QuestService(repository)
    val progressService = ProgressService(repository)
    val teacherService = TeacherService(repository)
    val recommendationService = RecommendationService()

    install(CallLogging) {
        level = Level.INFO
    }

    configureSerialization()
    configureStatusPages()
    configureHttp()
    configureSecurity(config.jwt, authService)
    registerRoutes(
        authService = authService,
        tokenService = tokenService,
        questService = questService,
        progressService = progressService,
        teacherService = teacherService,
        recommendationService = recommendationService
    )
}
