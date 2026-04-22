package dev.pan4ur.abitour.server.routes

import dev.pan4ur.abitour.server.service.AuthService
import dev.pan4ur.abitour.server.service.ProgressService
import dev.pan4ur.abitour.server.service.QuestService
import dev.pan4ur.abitour.server.service.RecommendationService
import dev.pan4ur.abitour.server.service.TeacherService
import dev.pan4ur.abitour.server.security.JwtTokenService
import io.ktor.server.application.call
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticFiles
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.io.File
import java.nio.file.Paths

fun Application.registerRoutes(
    authService: AuthService,
    tokenService: JwtTokenService,
    questService: QuestService,
    progressService: ProgressService,
    teacherService: TeacherService,
    recommendationService: RecommendationService
) {
    routing {
        get("/health") { call.respondText("ok") }
        get("/teacher-panel") { call.respondRedirect("/teacher-panel/index.html") }
        staticResources("/teacher-panel", "teacher-panel")
        val uploadsDir = resolveUploadsDir()
        if (!uploadsDir.exists()) uploadsDir.mkdirs()
        staticFiles("/uploads", uploadsDir)

        route("/api/v1") {
            authRoutes(authService, tokenService)

            authenticate("auth-jwt") {
                questRoutes(questService)
                progressRoutes(progressService, recommendationService)
                teacherRoutes(teacherService)
            }
        }
    }
}

internal fun resolveUploadsDir(): File {
    val envPath = System.getenv("ABITOUR_UPLOADS_DIR")?.trim().orEmpty()
    if (envPath.isNotEmpty()) {
        return File(envPath).absoluteFile
    }

    return Paths
        .get(System.getProperty("user.dir"))
        .toAbsolutePath()
        .normalize()
        .resolve("data")
        .resolve("uploads")
        .toFile()
}
