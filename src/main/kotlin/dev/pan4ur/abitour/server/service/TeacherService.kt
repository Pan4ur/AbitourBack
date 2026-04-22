package dev.pan4ur.abitour.server.service

import dev.pan4ur.abitour.server.dto.HintDto
import dev.pan4ur.abitour.server.dto.TeacherDeleteTaskResponse
import dev.pan4ur.abitour.server.dto.TeacherDeleteQuestResponse
import dev.pan4ur.abitour.server.dto.TeacherQuestSummaryDto
import dev.pan4ur.abitour.server.dto.TeacherQuestTeacherDto
import dev.pan4ur.abitour.server.dto.TeacherParticipantDetailsDto
import dev.pan4ur.abitour.server.dto.TeacherParticipantAnswerDto
import dev.pan4ur.abitour.server.dto.TeacherProgressDto
import dev.pan4ur.abitour.server.dto.TeacherQuestLocationDto
import dev.pan4ur.abitour.server.dto.TeacherTaskDto
import dev.pan4ur.abitour.server.dto.TeacherUpdateQuestResponse
import dev.pan4ur.abitour.server.dto.TeacherUpdateTaskResponse
import dev.pan4ur.abitour.server.model.UserRole
import dev.pan4ur.abitour.server.repository.BackendRepository
import dev.pan4ur.abitour.server.repository.TeacherQuestLocationRecord
import io.ktor.http.HttpStatusCode
import dev.pan4ur.abitour.server.error.ApiException

class TeacherService(
    private val repository: BackendRepository
) {
    fun progressFeed(teacherId: String): List<TeacherProgressDto> {
        val questId = repository.teacherQuests(teacherId).firstOrNull()?.questId ?: return emptyList()
        val questLocations = repository.questLocations(questId).sortedBy { it.position }
        val totalLocations = repository.totalLocations(questId)
        return repository.listUsers()
            .filter { it.role == UserRole.APPLICANT }
            .map { user ->
                val completedLocationIds = repository.scannedLocations(user.id, questId).toSet()
                val completed = completedLocationIds.size
                val score = repository.score(user.id, questId)
                val stats = repository.participantStats(user.id, questId)
                val currentLocationTitle = resolveCurrentLocationTitle(questLocations, completedLocationIds)
                TeacherProgressDto(
                    participantId = user.id,
                    participantName = user.name,
                    questId = questId,
                    currentLocationTitle = currentLocationTitle,
                    score = score,
                    completedLocations = completed,
                    totalLocations = totalLocations,
                    attemptsCount = stats.attemptsCount,
                    wrongAnswersCount = stats.wrongAnswersCount,
                    lastActivityAt = stats.lastActivityAt
                )
            }
    }

    fun sendHint(teacherId: String, participantId: String, hint: String): HintDto {
        val questId = repository.teacherQuests(teacherId).firstOrNull()?.questId
            ?: throw ApiException(HttpStatusCode.BadRequest, "QUEST_NOT_SELECTED", "Teacher has no quests")
        repository.saveHint(teacherId, participantId, questId, hint, "HINT")
        return HintDto(participantId = participantId, hint = hint)
    }

    fun sendRecommendation(teacherId: String, participantId: String, questId: String, recommendation: String): HintDto {
        if (questId.isBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_QUEST", "Quest id is required")
        }
        if (recommendation.isBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_RECOMMENDATION", "Recommendation is required")
        }
        requireQuestAccess(teacherId, questId)
        repository.saveHint(
            teacherId = teacherId,
            participantId = participantId,
            questId = questId,
            hint = recommendation.trim(),
            type = "RECOMMENDATION"
        )
        return HintDto(participantId = participantId, hint = recommendation.trim())
    }

    fun participantDetails(participantId: String, questId: String): TeacherParticipantDetailsDto {
        if (questId.isBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_QUEST", "Quest id is required")
        }
        val user = repository.findUser(participantId)
            ?: throw ApiException(HttpStatusCode.NotFound, "PARTICIPANT_NOT_FOUND", "Participant not found")
        if (user.role != UserRole.APPLICANT) {
            throw ApiException(HttpStatusCode.BadRequest, "NOT_APPLICANT", "User is not an applicant")
        }
        val completedLocationIds = repository.scannedLocations(participantId, questId).toList()
        val questLocations = repository.questLocations(questId).sortedBy { it.position }
        val totalLocations = repository.totalLocations(questId)
        val stats = repository.participantStats(participantId, questId)
        return TeacherParticipantDetailsDto(
            participantId = participantId,
            participantName = user.name,
            questId = questId,
            currentLocationTitle = resolveCurrentLocationTitle(questLocations, completedLocationIds.toSet()),
            score = repository.score(participantId, questId),
            completedLocations = completedLocationIds.size,
            totalLocations = totalLocations,
            attemptsCount = stats.attemptsCount,
            wrongAnswersCount = stats.wrongAnswersCount,
            completedLocationIds = completedLocationIds,
            lastActivityAt = stats.lastActivityAt
        )
    }

    fun participantAnswers(teacherId: String, participantId: String, questId: String): List<TeacherParticipantAnswerDto> {
        if (questId.isBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_QUEST", "Quest id is required")
        }
        requireQuestAccess(teacherId, questId)
        val user = repository.findUser(participantId)
            ?: throw ApiException(HttpStatusCode.NotFound, "PARTICIPANT_NOT_FOUND", "Participant not found")
        if (user.role != UserRole.APPLICANT) {
            throw ApiException(HttpStatusCode.BadRequest, "NOT_APPLICANT", "User is not an applicant")
        }
        return repository.participantAnswers(participantId, questId).map {
            TeacherParticipantAnswerDto(
                taskId = it.taskId,
                taskTitle = it.taskTitle,
                locationTitle = it.locationTitle,
                taskType = it.taskType,
                submittedAnswer = it.submittedAnswer,
                expectedAnswer = it.expectedAnswer,
                isCorrect = it.isCorrect,
                createdAt = it.createdAt
            )
        }
    }

    fun teacherQuests(teacherId: String): List<TeacherQuestSummaryDto> {
        return repository.teacherQuests(teacherId).map {
            TeacherQuestSummaryDto(
                questId = it.questId,
                title = it.title,
                description = it.description,
                institutionName = it.institutionName,
                isActive = it.isActive,
                ownerId = it.ownerId,
                ownerName = it.ownerName
            )
        }
    }

    fun createQuest(teacherId: String, title: String, description: String, institutionName: String, isActive: Boolean): String {
        if (title.isBlank() || description.isBlank() || institutionName.isBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_QUEST", "Quest fields are required")
        }
        return repository.createQuest(
            ownerId = teacherId,
            title = title.trim(),
            description = description.trim(),
            institutionName = institutionName.trim(),
            isActive = isActive
        )
    }

    fun updateQuest(
        teacherId: String,
        questId: String,
        title: String,
        description: String,
        institutionName: String,
        isActive: Boolean
    ): TeacherUpdateQuestResponse {
        requireQuestOwner(teacherId, questId)
        val updated = repository.updateQuest(
            questId = questId,
            title = title.trim(),
            description = description.trim(),
            institutionName = institutionName.trim(),
            isActive = isActive
        )
        return TeacherUpdateQuestResponse(updated = updated)
    }

    fun deleteQuest(teacherId: String, questId: String): TeacherDeleteQuestResponse {
        requireQuestOwner(teacherId, questId)
        val deleted = repository.deleteQuest(questId)
        return TeacherDeleteQuestResponse(deleted = deleted)
    }

    fun questTeachers(teacherId: String, questId: String): List<TeacherQuestTeacherDto> {
        requireQuestAccess(teacherId, questId)
        return repository.listQuestTeachers(questId).map {
            TeacherQuestTeacherDto(
                teacherId = it.teacherId,
                teacherName = it.teacherName,
                isOwner = it.isOwner
            )
        }
    }

    fun inviteTeacherToQuest(teacherId: String, questId: String, invitedTeacherName: String): Boolean {
        requireQuestOwner(teacherId, questId)
        if (invitedTeacherName.isBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_TEACHER_NAME", "Teacher name is required")
        }
        val account = repository.findAccount(invitedTeacherName.trim())
            ?: throw ApiException(HttpStatusCode.NotFound, "TEACHER_NOT_FOUND", "Teacher not found")
        if (account.role != UserRole.TEACHER) {
            throw ApiException(HttpStatusCode.BadRequest, "NOT_TEACHER", "Target user is not a teacher")
        }
        return repository.addTeacherToQuest(questId, account.id)
    }

    fun removeTeacherFromQuest(teacherId: String, questId: String, targetTeacherId: String): Boolean {
        requireQuestOwner(teacherId, questId)
        if (targetTeacherId == teacherId) {
            throw ApiException(HttpStatusCode.BadRequest, "OWNER_CANNOT_REMOVE_SELF", "Owner cannot remove self from quest")
        }
        if (repository.isQuestOwner(targetTeacherId, questId)) {
            throw ApiException(HttpStatusCode.BadRequest, "CANNOT_REMOVE_OWNER", "Quest owner cannot be removed")
        }
        return repository.removeTeacherFromQuest(questId, targetTeacherId)
    }

    fun questLocations(teacherId: String, questId: String): List<TeacherQuestLocationDto> {
        if (questId.isBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_QUEST", "Quest id is required")
        }
        requireQuestAccess(teacherId, questId)
        return repository.questLocations(questId).map {
            TeacherQuestLocationDto(
                locationId = it.locationId,
                position = it.position,
                title = it.title,
                qrCode = it.qrCode,
                tasksCount = it.tasksCount
            )
        }
    }

    fun createLocation(teacherId: String, questId: String, position: Int, title: String, qrCode: String): String {
        if (questId.isBlank() || title.isBlank() || qrCode.isBlank() || position <= 0) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_LOCATION", "Location fields are invalid")
        }
        requireQuestAccess(teacherId, questId)
        return repository.createLocation(
            questId = questId,
            position = position,
            title = title.trim(),
            qrCode = qrCode.trim()
        )
    }

    fun createTask(
        teacherId: String,
        locationId: String,
        title: String,
        description: String,
        taskType: String,
        maxScore: Int,
        options: List<String>,
        correctOptionIndex: Int?,
        correctAnswer: String?,
        mediaUrl: String?,
        mediaType: String?
    ): String {
        if (locationId.isBlank() || title.isBlank() || description.isBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_TASK", "Task fields are invalid")
        }
        val questId = repository.findQuestIdByLocation(locationId)
            ?: throw ApiException(HttpStatusCode.NotFound, "LOCATION_NOT_FOUND", "Location not found")
        requireQuestAccess(teacherId, questId)
        if (maxScore <= 0) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_TASK_SCORE", "Max score must be positive")
        }
        val normalizedType = normalizeTaskType(taskType)
        val normalizedOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
        val normalizedCorrectAnswer = correctAnswer?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedMediaUrl = mediaUrl?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedMediaType = mediaType?.trim()?.uppercase()?.takeIf { it == "IMAGE" || it == "VIDEO" }

        validateTaskPayload(
            taskType = normalizedType,
            options = normalizedOptions,
            correctOptionIndex = correctOptionIndex,
            correctAnswer = normalizedCorrectAnswer
        )

        return repository.createTask(
            locationId = locationId,
            title = title.trim(),
            description = description.trim(),
            taskType = normalizedType,
            maxScore = maxScore,
            options = normalizedOptions,
            correctOptionIndex = correctOptionIndex,
            correctAnswer = normalizedCorrectAnswer,
            mediaUrl = normalizedMediaUrl,
            mediaType = normalizedMediaType
        )
    }

    fun locationTasks(teacherId: String, locationId: String): List<TeacherTaskDto> {
        if (locationId.isBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_LOCATION", "Location id is required")
        }
        val questId = repository.findQuestIdByLocation(locationId)
            ?: throw ApiException(HttpStatusCode.NotFound, "LOCATION_NOT_FOUND", "Location not found")
        requireQuestAccess(teacherId, questId)
        return repository.locationTasks(locationId).map {
            TeacherTaskDto(
                taskId = it.taskId,
                locationId = it.locationId,
                title = it.title,
                description = it.description,
                taskType = it.taskType,
                maxScore = it.maxScore,
                options = it.options,
                correctOptionIndex = it.correctOptionIndex,
                correctAnswer = it.correctAnswer,
                mediaUrl = it.mediaUrl,
                mediaType = it.mediaType
            )
        }
    }

    fun updateTask(
        teacherId: String,
        taskId: String,
        title: String,
        description: String,
        taskType: String,
        maxScore: Int,
        options: List<String>,
        correctOptionIndex: Int?,
        correctAnswer: String?,
        mediaUrl: String?,
        mediaType: String?
    ): TeacherUpdateTaskResponse {
        if (taskId.isBlank() || title.isBlank() || description.isBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_TASK", "Task fields are invalid")
        }
        val questId = repository.findQuestIdByTask(taskId)
            ?: throw ApiException(HttpStatusCode.NotFound, "TASK_NOT_FOUND", "Task not found")
        requireQuestAccess(teacherId, questId)
        if (maxScore <= 0) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_TASK_SCORE", "Max score must be positive")
        }
        val normalizedType = normalizeTaskType(taskType)
        val normalizedOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
        val normalizedCorrectAnswer = correctAnswer?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedMediaUrl = mediaUrl?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedMediaType = mediaType?.trim()?.uppercase()?.takeIf { it == "IMAGE" || it == "VIDEO" }

        validateTaskPayload(
            taskType = normalizedType,
            options = normalizedOptions,
            correctOptionIndex = correctOptionIndex,
            correctAnswer = normalizedCorrectAnswer
        )

        val updated = repository.updateTask(
            taskId = taskId,
            title = title.trim(),
            description = description.trim(),
            taskType = normalizedType,
            maxScore = maxScore,
            options = normalizedOptions,
            correctOptionIndex = correctOptionIndex,
            correctAnswer = normalizedCorrectAnswer,
            mediaUrl = normalizedMediaUrl,
            mediaType = normalizedMediaType
        )
        return TeacherUpdateTaskResponse(updated = updated)
    }

    fun deleteTask(teacherId: String, taskId: String): TeacherDeleteTaskResponse {
        if (taskId.isBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_TASK", "Task id is required")
        }
        val questId = repository.findQuestIdByTask(taskId)
            ?: throw ApiException(HttpStatusCode.NotFound, "TASK_NOT_FOUND", "Task not found")
        requireQuestAccess(teacherId, questId)
        val deleted = repository.deleteTask(taskId)
        return TeacherDeleteTaskResponse(deleted = deleted)
    }

    private fun requireQuestAccess(teacherId: String, questId: String) {
        if (!repository.hasTeacherQuestAccess(teacherId, questId)) {
            throw ApiException(HttpStatusCode.Forbidden, "QUEST_FORBIDDEN", "You do not have access to this quest")
        }
    }

    private fun requireQuestOwner(teacherId: String, questId: String) {
        if (!repository.isQuestOwner(teacherId, questId)) {
            throw ApiException(HttpStatusCode.Forbidden, "QUEST_OWNER_REQUIRED", "Only quest owner can perform this action")
        }
    }

    private fun normalizeTaskType(raw: String): String {
        return when (raw.trim().uppercase()) {
            "QUIZ" -> "QUIZ"
            "INFO" -> "INFO"
            "QUESTION", "OPEN", "CODE", "MATCH" -> "QUESTION"
            else -> throw ApiException(HttpStatusCode.BadRequest, "INVALID_TASK_TYPE", "Unsupported task type")
        }
    }

    private fun validateTaskPayload(
        taskType: String,
        options: List<String>,
        correctOptionIndex: Int?,
        correctAnswer: String?
    ) {
        when (taskType) {
            "QUIZ" -> {
                if (options.size < 2) {
                    throw ApiException(HttpStatusCode.BadRequest, "INVALID_QUIZ_OPTIONS", "Quiz must contain at least 2 options")
                }
                if (correctOptionIndex == null || correctOptionIndex !in options.indices) {
                    throw ApiException(HttpStatusCode.BadRequest, "INVALID_QUIZ_CORRECT_OPTION", "Correct option index is invalid")
                }
            }
            "INFO" -> Unit
            "QUESTION" -> {
                if (correctAnswer.isNullOrBlank()) {
                    throw ApiException(HttpStatusCode.BadRequest, "INVALID_QUESTION_ANSWER", "Correct answer is required for QUESTION")
                }
            }
        }
    }

    private fun resolveCurrentLocationTitle(
        questLocations: List<TeacherQuestLocationRecord>,
        completedLocationIds: Set<String>
    ): String? {
        if (questLocations.isEmpty()) return null
        return questLocations.firstOrNull { it.locationId !in completedLocationIds }?.title
            ?: questLocations.lastOrNull()?.title
    }
}
