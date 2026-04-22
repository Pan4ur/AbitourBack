package dev.pan4ur.abitour.server.service

import dev.pan4ur.abitour.server.dto.AnswerRequest
import dev.pan4ur.abitour.server.dto.QuestDto
import dev.pan4ur.abitour.server.dto.TaskDto
import dev.pan4ur.abitour.server.error.ApiException
import dev.pan4ur.abitour.server.repository.BackendRepository
import io.ktor.http.HttpStatusCode

class QuestService(
    private val repository: BackendRepository
) {
    fun activeQuests(): List<QuestDto> {
        return repository.activeQuests().map {
            QuestDto(
                it.id,
                it.title,
                it.description,
                it.institutionName
            )
        }
    }

    fun tasksByLocation(userId: String, locationId: String): List<TaskDto> {
        val tasks = repository.tasksByLocation(locationId)
        if (tasks.isEmpty()) {
            throw ApiException(HttpStatusCode.NotFound, "LOCATION_NOT_FOUND", "Location not found")
        }
        val questId = repository.findQuestIdByLocation(locationId)
            ?: throw ApiException(HttpStatusCode.NotFound, "LOCATION_NOT_FOUND", "Location not found")
        return tasks.map {
            TaskDto(
                it.id,
                it.locationId,
                it.title,
                it.description,
                normalizeTaskType(it.taskType),
                it.options.map { option -> dev.pan4ur.abitour.server.dto.TaskOptionDto(option.text) },
                it.mediaUrl,
                it.mediaType,
                it.code,
                repository.hasSubmittedAnswer(userId, questId, it.id)
            )
        }
    }

    fun tasksByQuest(userId: String, questId: String): List<TaskDto> {
        if (questId.isBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_QUEST", "Quest id is required")
        }
        return repository.tasksByQuest(questId).map {
            TaskDto(
                it.id,
                it.locationId,
                it.title,
                it.description,
               normalizeTaskType(it.taskType),
                it.options.map { option -> dev.pan4ur.abitour.server.dto.TaskOptionDto(option.text) },
                it.mediaUrl,
                it.mediaType,
                it.code,
                repository.hasSubmittedAnswer(userId, questId, it.id)
            )
        }
    }

    fun scan(userId: String, questId: String, qrCode: String): String {
        val normalizedQr = qrCode.trim()
        val legacyPrefix = "ABITOUR-QR-"
        val candidates = linkedSetOf(
            normalizedQr,
            normalizedQr.removePrefix(legacyPrefix),
            "$legacyPrefix${normalizedQr.removePrefix(legacyPrefix)}"
        )
        val point = candidates
            .asSequence()
            .mapNotNull { repository.findLocationByQr(it) }
            .firstOrNull()
            ?: throw ApiException(HttpStatusCode.BadRequest, "INVALID_QR", "QR code is not recognized")
        if (point.questId != questId) {
            throw ApiException(HttpStatusCode.Conflict, "QUEST_MISMATCH", "QR belongs to another quest")
        }

        val completed = repository.scannedLocations(userId, questId)
        if (completed.contains(point.locationId)) {
            return point.locationId
        }
        val expectedOrder = completed.size + 1
        if (point.order != expectedOrder) {
            throw ApiException(
                HttpStatusCode.Conflict,
                "INVALID_ROUTE_ORDER",
                "Expected point #$expectedOrder, but scanned #${point.order}"
            )
        }

        repository.markLocationScanned(userId, questId, point.locationId)
        return point.locationId
    }

    fun submitAnswer(userId: String, questId: String, taskId: String, request: AnswerRequest): Boolean {
        val task = repository.findTask(taskId)
            ?: throw ApiException(HttpStatusCode.NotFound, "TASK_NOT_FOUND", "Task not found")
        val taskType = normalizeTaskType(task.taskType)
        val normalizedAnswer = request.answer.trim()

        if (taskType != "INFO" && normalizedAnswer.isBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "EMPTY_ANSWER", "Answer must not be blank")
        }

        val isCorrect = when (taskType) {
            "INFO" -> true
            "QUESTION" -> matchesQuestionAnswer(task.correctAnswer, normalizedAnswer)
            else -> task.correctAnswer?.trim()?.equals(normalizedAnswer, ignoreCase = true) == true
        }

        val scoreAwarded = 0
        repository.saveAnswer(
            userId,
            questId,
            taskId,
            normalizedAnswer,
            isCorrect,
            scoreAwarded
        )
        return true
    }

    private fun normalizeTaskType(raw: String): String {
        return when (raw.trim().uppercase()) {
            "QUIZ" -> "QUIZ"
            "INFO" -> "INFO"
            "QUESTION", "OPEN", "CODE", "MATCH" -> "QUESTION"
            else -> "QUESTION"
        }
    }

    private fun matchesQuestionAnswer(expectedAnswer: String?, actualAnswer: String): Boolean {
        return expectedAnswer
            ?.split(';')
            ?.asSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.any { it.equals(actualAnswer, ignoreCase = true) }
            ?: false
    }
}
