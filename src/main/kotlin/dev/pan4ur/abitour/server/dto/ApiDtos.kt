package dev.pan4ur.abitour.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val name: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val name: String,
    val role: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: UserDto
)

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val role: String
)

@Serializable
data class QuestDto(
    val id: String,
    val title: String,
    val description: String,
    val institutionName: String
)

@Serializable
data class TaskDto(
    val id: String,
    val locationId: String,
    val title: String,
    val description: String,
    val taskType: String,
    val options: List<TaskOptionDto> = emptyList(),
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val code: String,
    val isCompleted: Boolean = false
)

@Serializable
data class TaskOptionDto(
    val text: String
)

@Serializable
data class ScanRequest(
    val qrCode: String
)

@Serializable
data class AnswerRequest(
    val answer: String
)

@Serializable
data class ProgressDto(
    val userId: String,
    val questId: String,
    val completedLocations: Int,
    val totalLocations: Int,
    val score: Int
)

@Serializable
data class ResultDto(
    val userId: String,
    val questId: String,
    val score: Int,
    val recommendation: String
)

@Serializable
data class TeacherProgressDto(
    val participantId: String,
    val participantName: String,
    val questId: String,
    val currentLocationTitle: String?,
    val score: Int,
    val completedLocations: Int,
    val totalLocations: Int,
    val attemptsCount: Int,
    val wrongAnswersCount: Int,
    val lastActivityAt: String?
)

@Serializable
data class HintRequest(
    val participantId: String,
    val hint: String
)

@Serializable
data class TeacherRecommendationRequest(
    val participantId: String,
    val questId: String,
    val recommendation: String
)

@Serializable
data class HintDto(
    val participantId: String,
    val hint: String
)

@Serializable
data class ApplicantHintDto(
    val id: String,
    val hint: String,
    val createdAt: String,
    val isRead: Boolean
)

@Serializable
data class TeacherParticipantDetailsDto(
    val participantId: String,
    val participantName: String,
    val questId: String,
    val currentLocationTitle: String?,
    val score: Int,
    val completedLocations: Int,
    val totalLocations: Int,
    val attemptsCount: Int,
    val wrongAnswersCount: Int,
    val completedLocationIds: List<String>,
    val lastActivityAt: String?
)

@Serializable
data class TeacherParticipantAnswerDto(
    val taskId: String,
    val taskTitle: String,
    val locationTitle: String,
    val taskType: String,
    val submittedAnswer: String,
    val expectedAnswer: String?,
    val isCorrect: Boolean,
    val createdAt: String
)

@Serializable
data class TeacherCreateQuestRequest(
    val title: String,
    val description: String,
    val institutionName: String,
    val isActive: Boolean = true
)

@Serializable
data class TeacherCreateQuestResponse(
    val questId: String
)

@Serializable
data class TeacherQuestSummaryDto(
    val questId: String,
    val title: String,
    val description: String,
    val institutionName: String,
    val isActive: Boolean,
    val ownerId: String?,
    val ownerName: String?
)

@Serializable
data class TeacherUpdateQuestRequest(
    val title: String,
    val description: String,
    val institutionName: String,
    val isActive: Boolean = true
)

@Serializable
data class TeacherUpdateQuestResponse(
    val updated: Boolean
)

@Serializable
data class TeacherDeleteQuestResponse(
    val deleted: Boolean
)

@Serializable
data class TeacherQuestTeacherDto(
    val teacherId: String,
    val teacherName: String,
    val isOwner: Boolean
)

@Serializable
data class TeacherInviteToQuestRequest(
    val teacherName: String
)

@Serializable
data class TeacherQuestTeacherMutationResponse(
    val success: Boolean
)

@Serializable
data class TeacherCreateLocationRequest(
    val position: Int,
    val title: String,
    val qrCode: String
)

@Serializable
data class TeacherCreateLocationResponse(
    val locationId: String,
    val position: Int,
    val title: String,
    val qrCode: String
)

@Serializable
data class TeacherQuestLocationDto(
    val locationId: String,
    val position: Int,
    val title: String,
    val qrCode: String,
    val tasksCount: Int
)

@Serializable
data class TeacherCreateTaskRequest(
    val title: String,
    val description: String,
    val taskType: String,
    val maxScore: Int = 1,
    val options: List<String> = emptyList(),
    val correctOptionIndex: Int? = null,
    val correctAnswer: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null
)

@Serializable
data class TeacherCreateTaskResponse(
    val taskId: String
)

@Serializable
data class TeacherTaskDto(
    val taskId: String,
    val locationId: String,
    val title: String,
    val description: String,
    val taskType: String,
    val maxScore: Int,
    val options: List<String> = emptyList(),
    val correctOptionIndex: Int? = null,
    val correctAnswer: String?,
    val mediaUrl: String? = null,
    val mediaType: String? = null
)

@Serializable
data class TeacherUpdateTaskRequest(
    val title: String,
    val description: String,
    val taskType: String,
    val maxScore: Int = 1,
    val options: List<String> = emptyList(),
    val correctOptionIndex: Int? = null,
    val correctAnswer: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null
)

@Serializable
data class TeacherUploadResponse(
    val url: String,
    val mediaType: String
)

@Serializable
data class TeacherUpdateTaskResponse(
    val updated: Boolean
)

@Serializable
data class TeacherDeleteTaskResponse(
    val deleted: Boolean
)

@Serializable
data class ApiError(
    val code: String,
    val message: String
)
