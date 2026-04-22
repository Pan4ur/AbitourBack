package dev.pan4ur.abitour.server.model

enum class UserRole {
    APPLICANT,
    TEACHER
}

data class User(
    val id: String,
    val name: String,
    val role: UserRole
)

data class Quest(
    val id: String,
    val title: String,
    val description: String,
    val institutionName: String
)

data class Task(
    val id: String,
    val locationId: String,
    val title: String,
    val description: String,
    val taskType: String,
    val options: List<TaskOption> = emptyList(),
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val code: String,
    val correctAnswer: String? = null
)

data class TaskOption(
    val text: String,
    val isCorrect: Boolean
)
