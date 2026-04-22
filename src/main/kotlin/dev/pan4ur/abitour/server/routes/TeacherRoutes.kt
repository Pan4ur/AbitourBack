package dev.pan4ur.abitour.server.routes

import dev.pan4ur.abitour.server.dto.HintRequest
import dev.pan4ur.abitour.server.dto.TeacherRecommendationRequest
import dev.pan4ur.abitour.server.dto.TeacherCreateLocationRequest
import dev.pan4ur.abitour.server.dto.TeacherCreateLocationResponse
import dev.pan4ur.abitour.server.dto.TeacherCreateQuestRequest
import dev.pan4ur.abitour.server.dto.TeacherCreateQuestResponse
import dev.pan4ur.abitour.server.dto.TeacherCreateTaskRequest
import dev.pan4ur.abitour.server.dto.TeacherCreateTaskResponse
import dev.pan4ur.abitour.server.dto.TeacherDeleteQuestResponse
import dev.pan4ur.abitour.server.dto.TeacherInviteToQuestRequest
import dev.pan4ur.abitour.server.dto.TeacherQuestTeacherMutationResponse
import dev.pan4ur.abitour.server.dto.TeacherUploadResponse
import dev.pan4ur.abitour.server.dto.TeacherUpdateTaskRequest
import dev.pan4ur.abitour.server.dto.TeacherUpdateQuestRequest
import dev.pan4ur.abitour.server.error.ApiException
import dev.pan4ur.abitour.server.model.UserRole
import dev.pan4ur.abitour.server.service.TeacherService
import dev.pan4ur.abitour.server.service.QrPrintPdfService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.delete
import io.ktor.server.routing.put
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.core.readBytes
import java.io.File
import java.util.UUID

fun Route.teacherRoutes(teacherService: TeacherService) {
    val qrPrintPdfService = QrPrintPdfService()
    route("/teacher") {
        get("/progress") {
            requireRole(call, UserRole.TEACHER)
            val teacherId = requireUserId(call)
            call.respond(teacherService.progressFeed(teacherId))
        }

        get("/quests") {
            requireRole(call, UserRole.TEACHER)
            val teacherId = requireUserId(call)
            call.respond(teacherService.teacherQuests(teacherId))
        }

        post("/hint") {
            requireRole(call, UserRole.TEACHER)
            val teacherId = requireUserId(call)
            val payload = call.receive<HintRequest>()
            call.respond(teacherService.sendHint(teacherId, payload.participantId, payload.hint))
        }

        post("/recommendation") {
            requireRole(call, UserRole.TEACHER)
            val teacherId = requireUserId(call)
            val payload = call.receive<TeacherRecommendationRequest>()
            call.respond(
                teacherService.sendRecommendation(
                    teacherId = teacherId,
                    participantId = payload.participantId,
                    questId = payload.questId,
                    recommendation = payload.recommendation
                )
            )
        }

        post("/media/upload") {
            requireRole(call, UserRole.TEACHER)
            requireUserId(call)
            val uploadsDir = resolveUploadsDir().also { if (!it.exists()) it.mkdirs() }
            val multipart = call.receiveMultipart()

            var uploaded: TeacherUploadResponse? = null
            multipart.forEachPart { part ->
                try {
                    if (part is PartData.FileItem && part.name == "file" && uploaded == null) {
                        uploaded = saveUploadedMedia(part, uploadsDir)
                    }
                } finally {
                    part.dispose()
                }
            }

            call.respond(
                uploaded ?: throw ApiException(
                    HttpStatusCode.BadRequest,
                    "UPLOAD_EMPTY",
                    "Файл не передан"
                )
            )
        }

        get("/participants/{participantId}") {
            requireRole(call, UserRole.TEACHER)
            val participantId = call.parameters["participantId"].orEmpty()
            val questId = call.request.queryParameters["questId"].orEmpty()
            call.respond(teacherService.participantDetails(participantId, questId))
        }

        get("/participants/{participantId}/answers") {
            requireRole(call, UserRole.TEACHER)
            val teacherId = requireUserId(call)
            val participantId = call.parameters["participantId"].orEmpty()
            val questId = call.request.queryParameters["questId"].orEmpty()
            call.respond(teacherService.participantAnswers(teacherId, participantId, questId))
        }

        post("/quests") {
            requireRole(call, UserRole.TEACHER)
            val teacherId = requireUserId(call)
            val payload = call.receive<TeacherCreateQuestRequest>()
            val questId = teacherService.createQuest(
                teacherId = teacherId,
                title = payload.title,
                description = payload.description,
                institutionName = payload.institutionName,
                isActive = payload.isActive
            )
            call.respond(TeacherCreateQuestResponse(questId))
        }

        put("/quests/{questId}") {
            requireRole(call, UserRole.TEACHER)
            val teacherId = requireUserId(call)
            val questId = call.parameters["questId"].orEmpty()
            val payload = call.receive<TeacherUpdateQuestRequest>()
            call.respond(
                teacherService.updateQuest(
                    teacherId = teacherId,
                    questId = questId,
                    title = payload.title,
                    description = payload.description,
                    institutionName = payload.institutionName,
                    isActive = payload.isActive
                )
            )
        }

        delete("/quests/{questId}") {
            requireRole(call, UserRole.TEACHER)
            val teacherId = requireUserId(call)
            val questId = call.parameters["questId"].orEmpty()
            val result = teacherService.deleteQuest(teacherId, questId)
            call.respond(TeacherDeleteQuestResponse(result.deleted))
        }

        get("/quests/{questId}/teachers") {
            requireRole(call, UserRole.TEACHER)
            val teacherId = requireUserId(call)
            val questId = call.parameters["questId"].orEmpty()
            call.respond(teacherService.questTeachers(teacherId, questId))
        }

        post("/quests/{questId}/teachers/invite") {
            requireRole(call, UserRole.TEACHER)
            val teacherId = requireUserId(call)
            val questId = call.parameters["questId"].orEmpty()
            val payload = call.receive<TeacherInviteToQuestRequest>()
            val success = teacherService.inviteTeacherToQuest(teacherId, questId, payload.teacherName)
            call.respond(TeacherQuestTeacherMutationResponse(success))
        }

        delete("/quests/{questId}/teachers/{targetTeacherId}") {
            requireRole(call, UserRole.TEACHER)
            val teacherId = requireUserId(call)
            val questId = call.parameters["questId"].orEmpty()
            val targetTeacherId = call.parameters["targetTeacherId"].orEmpty()
            val success = teacherService.removeTeacherFromQuest(teacherId, questId, targetTeacherId)
            call.respond(TeacherQuestTeacherMutationResponse(success))
        }

        get("/quests/{questId}/locations") {
            requireRole(call, UserRole.TEACHER)
            val teacherId = requireUserId(call)
            val questId = call.parameters["questId"].orEmpty()
            call.respond(teacherService.questLocations(teacherId, questId))
        }

        post("/quests/{questId}/locations") {
            requireRole(call, UserRole.TEACHER)
            val teacherId = requireUserId(call)
            val questId = call.parameters["questId"].orEmpty()
            val payload = call.receive<TeacherCreateLocationRequest>()
            val locationId = teacherService.createLocation(
                teacherId = teacherId,
                questId = questId,
                position = payload.position,
                title = payload.title,
                qrCode = payload.qrCode
            )
            call.respond(
                TeacherCreateLocationResponse(
                    locationId = locationId,
                    position = payload.position,
                    title = payload.title,
                    qrCode = payload.qrCode
                )
            )
        }

        post("/locations/{locationId}/tasks") {
            requireRole(call, UserRole.TEACHER)
            val teacherId = requireUserId(call)
            val locationId = call.parameters["locationId"].orEmpty()
            val payload = call.receive<TeacherCreateTaskRequest>()
            val taskId = teacherService.createTask(
                teacherId = teacherId,
                locationId = locationId,
                title = payload.title,
                description = payload.description,
                taskType = payload.taskType,
                maxScore = payload.maxScore,
                options = payload.options,
                correctOptionIndex = payload.correctOptionIndex,
                correctAnswer = payload.correctAnswer,
                mediaUrl = payload.mediaUrl,
                mediaType = payload.mediaType
            )
            call.respond(TeacherCreateTaskResponse(taskId))
        }

        get("/locations/{locationId}/tasks") {
            requireRole(call, UserRole.TEACHER)
            val teacherId = requireUserId(call)
            val locationId = call.parameters["locationId"].orEmpty()
            call.respond(teacherService.locationTasks(teacherId, locationId))
        }

        get("/locations/{locationId}/qr-print.pdf") {
            requireRole(call, UserRole.TEACHER)
            val teacherId = requireUserId(call)
            val locationId = call.parameters["locationId"].orEmpty()
            if (locationId.isBlank()) {
                throw ApiException(HttpStatusCode.BadRequest, "INVALID_LOCATION", "Location id is required")
            }

            val quest = teacherService.teacherQuests(teacherId).firstOrNull { quest ->
                teacherService.questLocations(teacherId, quest.questId).any { it.locationId == locationId }
            } ?: throw ApiException(HttpStatusCode.NotFound, "LOCATION_NOT_FOUND", "Location not found")

            val location = teacherService.questLocations(teacherId, quest.questId).firstOrNull { it.locationId == locationId }
                ?: throw ApiException(HttpStatusCode.NotFound, "LOCATION_NOT_FOUND", "Location not found")

            val templateBytes = this::class.java.classLoader
                .getResourceAsStream("teacher-panel/QR_TEMPLATE.png")
                ?.readBytes()
                ?: throw ApiException(
                    HttpStatusCode.InternalServerError,
                    "QR_TEMPLATE_MISSING",
                    "QR template not found"
                )

            val pdfBytes = qrPrintPdfService.buildPdf(
                templatePngBytes = templateBytes,
                qrCode = location.qrCode,
                routeTitle = quest.title
            )

            val safeTitle = location.title.replace(Regex("[^a-zA-Z0-9а-яА-Я_-]+"), "_")
            call.response.header(
                HttpHeaders.ContentDisposition,
                """attachment; filename="qr-print-${safeTitle.ifBlank { "point" }}.pdf""""
            )
            call.respondBytes(pdfBytes, ContentType.Application.Pdf)
        }

        put("/tasks/{taskId}") {
            requireRole(call, UserRole.TEACHER)
            val teacherId = requireUserId(call)
            val taskId = call.parameters["taskId"].orEmpty()
            val payload = call.receive<TeacherUpdateTaskRequest>()
            call.respond(
                teacherService.updateTask(
                    teacherId = teacherId,
                    taskId = taskId,
                    title = payload.title,
                    description = payload.description,
                    taskType = payload.taskType,
                    maxScore = payload.maxScore,
                    options = payload.options,
                    correctOptionIndex = payload.correctOptionIndex,
                    correctAnswer = payload.correctAnswer,
                    mediaUrl = payload.mediaUrl,
                    mediaType = payload.mediaType
                )
            )
        }

        delete("/tasks/{taskId}") {
            requireRole(call, UserRole.TEACHER)
            val teacherId = requireUserId(call)
            val taskId = call.parameters["taskId"].orEmpty()
            call.respond(teacherService.deleteTask(teacherId, taskId))
        }
    }
}

private fun saveUploadedMedia(part: PartData.FileItem, uploadsDir: File): TeacherUploadResponse {
    val contentType = part.contentType ?: throw ApiException(
        HttpStatusCode.BadRequest,
        "UPLOAD_CONTENT_TYPE_REQUIRED",
        "Не указан тип файла"
    )

    val mediaType = when (contentType.contentType.lowercase()) {
        "image" -> "IMAGE"
        "video" -> "VIDEO"
        else -> throw ApiException(
            HttpStatusCode.BadRequest,
            "UPLOAD_UNSUPPORTED_TYPE",
            "Поддерживаются только изображения и видео"
        )
    }

    val extension = determineExtension(contentType, part.originalFileName, mediaType)
    val filename = "${System.currentTimeMillis()}-${UUID.randomUUID()}.$extension"
    val target = uploadsDir.resolve(filename)

    val bytes = part.provider().readBytes()
    target.outputStream().use { output ->
        output.write(bytes)
    }

    return TeacherUploadResponse(
        url = "/uploads/$filename",
        mediaType = mediaType
    )
}

private fun determineExtension(contentType: ContentType, originalName: String?, mediaType: String): String {
    val subtype = contentType.contentSubtype.lowercase()
    val fromContentType = when (subtype) {
        "jpeg", "jpg", "png", "gif", "webp", "bmp", "mp4", "webm", "ogg", "quicktime" -> subtype
        "x-msvideo" -> "avi"
        else -> null
    }
    if (fromContentType != null) {
        return if (fromContentType == "jpeg") "jpg" else fromContentType
    }

    val fromName = originalName
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase()
        ?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
    if (!fromName.isNullOrBlank()) return fromName

    return if (mediaType == "VIDEO") "mp4" else "jpg"
}
