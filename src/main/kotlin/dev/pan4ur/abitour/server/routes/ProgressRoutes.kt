package dev.pan4ur.abitour.server.routes

import dev.pan4ur.abitour.server.model.UserRole
import dev.pan4ur.abitour.server.service.ProgressService
import dev.pan4ur.abitour.server.service.RecommendationService
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.http.HttpStatusCode

fun Route.progressRoutes(
    progressService: ProgressService,
    recommendationService: RecommendationService
) {
    get("/progress") {
        requireRole(call, UserRole.APPLICANT)
        val userId = requireUserId(call)
        val questId = call.request.queryParameters["questId"].orEmpty()
        call.respond(progressService.progress(userId, questId))
    }

    get("/result") {
        requireRole(call, UserRole.APPLICANT)
        val userId = requireUserId(call)
        val questId = call.request.queryParameters["questId"].orEmpty()
        val progress = progressService.progress(userId, questId)
        val isCompleted = progress.totalLocations > 0 && progress.completedLocations >= progress.totalLocations
        if (!isCompleted) {
            call.response.status(HttpStatusCode.Conflict)
            call.respond(mapOf("message" to "Quest is not completed yet"))
            return@get
        }
        val recommendation = progressService.latestRecommendation(userId, questId).orEmpty()
        call.respond(progressService.result(userId, questId, recommendation))
    }

    get("/hints") {
        requireRole(call, UserRole.APPLICANT)
        val userId = requireUserId(call)
        val questId = call.request.queryParameters["questId"].orEmpty()
        call.respond(progressService.hints(userId, questId))
    }

    post("/hints/read") {
        requireRole(call, UserRole.APPLICANT)
        val userId = requireUserId(call)
        val questId = call.request.queryParameters["questId"].orEmpty()
        progressService.markHintsRead(userId, questId)
        call.respond(mapOf("ok" to true))
    }
}
