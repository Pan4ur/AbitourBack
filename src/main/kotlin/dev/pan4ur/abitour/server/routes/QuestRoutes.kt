package dev.pan4ur.abitour.server.routes

import dev.pan4ur.abitour.server.dto.AnswerRequest
import dev.pan4ur.abitour.server.dto.ScanRequest
import dev.pan4ur.abitour.server.model.UserRole
import dev.pan4ur.abitour.server.service.QuestService
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.questRoutes(questService: QuestService) {
    route("/quests") {
        get("/active") {
            requireRole(call, UserRole.APPLICANT)
            call.respond(questService.activeQuests())
        }

        get("/{questId}/tasks") {
            requireRole(call, UserRole.APPLICANT)
            val userId = requireUserId(call)
            val questId = call.parameters["questId"].orEmpty()
            call.respond(questService.tasksByQuest(userId, questId))
        }
    }

    route("/tasks") {
        get("/{locationId}") {
            requireRole(call, UserRole.APPLICANT)
            val userId = requireUserId(call)
            val locationId = call.parameters["locationId"].orEmpty()
            call.respond(questService.tasksByLocation(userId, locationId))
        }

        post("/{taskId}/answer") {
            requireRole(call, UserRole.APPLICANT)
            val userId = requireUserId(call)
            val questId = call.request.queryParameters["questId"].orEmpty()
            val taskId = call.parameters["taskId"].orEmpty()
            val payload = call.receive<AnswerRequest>()
            call.respond(mapOf("accepted" to questService.submitAnswer(userId, questId, taskId, payload)))
        }
    }

    post("/scan") {
        requireRole(call, UserRole.APPLICANT)
        val userId = requireUserId(call)
        val questId = call.request.queryParameters["questId"].orEmpty()
        val payload = call.receive<ScanRequest>()
        val locationId = questService.scan(userId, questId, payload.qrCode)
        call.respond(mapOf("locationId" to locationId))
    }
}
