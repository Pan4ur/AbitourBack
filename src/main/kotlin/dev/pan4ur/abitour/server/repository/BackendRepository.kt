package dev.pan4ur.abitour.server.repository

import dev.pan4ur.abitour.server.model.Quest
import dev.pan4ur.abitour.server.model.Task
import dev.pan4ur.abitour.server.model.User
import dev.pan4ur.abitour.server.model.UserRole

data class AuthAccount(
    val id: String,
    val name: String,
    val role: UserRole,
    val passwordHash: String
)

data class LocationScanPoint(
    val questId: String,
    val locationId: String,
    val order: Int
)

data class TeacherParticipantStats(
    val attemptsCount: Int,
    val wrongAnswersCount: Int,
    val lastActivityAt: String?
)

data class ApplicantHintRecord(
    val id: String,
    val hint: String,
    val createdAt: String,
    val isRead: Boolean,
    val type: String
)

data class ParticipantAnswerRecord(
    val taskId: String,
    val taskTitle: String,
    val locationTitle: String,
    val taskType: String,
    val submittedAnswer: String,
    val expectedAnswer: String?,
    val isCorrect: Boolean,
    val createdAt: String
)

data class TeacherQuestLocationRecord(
    val locationId: String,
    val position: Int,
    val title: String,
    val qrCode: String,
    val tasksCount: Int
)

data class TeacherTaskRecord(
    val taskId: String,
    val locationId: String,
    val title: String,
    val description: String,
    val taskType: String,
    val maxScore: Int,
    val options: List<String>,
    val correctOptionIndex: Int?,
    val correctAnswer: String?,
    val mediaUrl: String?,
    val mediaType: String?
)

data class TeacherQuestSummaryRecord(
    val questId: String,
    val title: String,
    val description: String,
    val institutionName: String,
    val isActive: Boolean,
    val ownerId: String?,
    val ownerName: String?
)

data class TeacherQuestTeacherRecord(
    val teacherId: String,
    val teacherName: String,
    val isOwner: Boolean
)

interface BackendRepository {
    fun createUser(name: String, role: UserRole, passwordHash: String): User
    fun findAccount(name: String): AuthAccount?
    fun findUser(userId: String): User?
    fun findUserRole(userId: String): UserRole?
    fun findUserName(userId: String): String?
    fun listUsers(): List<User>

    fun activeQuests(): List<Quest>
    fun tasksByQuest(questId: String): List<Task>
    fun tasksByLocation(locationId: String): List<Task>
    fun findTask(taskId: String): Task?
    fun findLocationByQr(qrCode: String): LocationScanPoint?
    fun totalLocations(questId: String): Int

    fun scannedLocations(userId: String, questId: String): Set<String>
    fun markLocationScanned(userId: String, questId: String, locationId: String)
    fun score(userId: String, questId: String): Int
    fun addScore(userId: String, questId: String, scoreDelta: Int)
    fun hasSubmittedAnswer(userId: String, questId: String, taskId: String): Boolean
    fun saveAnswer(
        userId: String,
        questId: String,
        taskId: String,
        answerText: String,
        isCorrect: Boolean,
        scoreAwarded: Int
    )

    fun saveHint(teacherId: String, participantId: String, questId: String, hint: String, type: String = "HINT")
    fun listHints(userId: String, questId: String): List<ApplicantHintRecord>
    fun latestRecommendation(userId: String, questId: String): String?
    fun participantAnswers(userId: String, questId: String): List<ParticipantAnswerRecord>
    fun markHintsViewed(userId: String, questId: String)
    fun participantStats(userId: String, questId: String): TeacherParticipantStats

    fun allQuests(): List<Quest>
    fun teacherQuests(teacherId: String): List<TeacherQuestSummaryRecord>
    fun hasTeacherQuestAccess(teacherId: String, questId: String): Boolean
    fun isQuestOwner(teacherId: String, questId: String): Boolean
    fun updateQuest(questId: String, title: String, description: String, institutionName: String, isActive: Boolean): Boolean
    fun deleteQuest(questId: String): Boolean
    fun listQuestTeachers(questId: String): List<TeacherQuestTeacherRecord>
    fun addTeacherToQuest(questId: String, teacherId: String): Boolean
    fun removeTeacherFromQuest(questId: String, teacherId: String): Boolean
    fun findQuestIdByLocation(locationId: String): String?
    fun findQuestIdByTask(taskId: String): String?
    fun questLocations(questId: String): List<TeacherQuestLocationRecord>
    fun locationTasks(locationId: String): List<TeacherTaskRecord>
    fun createQuest(ownerId: String, title: String, description: String, institutionName: String, isActive: Boolean): String
    fun createLocation(questId: String, position: Int, title: String, qrCode: String): String
    fun createTask(
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
    ): String
    fun updateTask(
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
    ): Boolean
    fun deleteTask(taskId: String): Boolean
}
