package dev.pan4ur.abitour.server.repository

import com.zaxxer.hikari.HikariDataSource
import dev.pan4ur.abitour.server.model.Quest
import dev.pan4ur.abitour.server.model.Task
import dev.pan4ur.abitour.server.model.TaskOption
import dev.pan4ur.abitour.server.model.User
import dev.pan4ur.abitour.server.model.UserRole
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

class JdbcBackendRepository(
    private val dataSource: HikariDataSource
) : BackendRepository {
    override fun createUser(name: String, role: UserRole, passwordHash: String): User {
        val userId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO users (id, name, role, password_hash)
                VALUES (?, ?, ?, ?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, userId.toString())
                stmt.setString(2, name)
                stmt.setString(3, role.name)
                stmt.setString(4, passwordHash)
                stmt.executeUpdate()
            }
        }
        return User(id = userId.toString(), name = name, role = role)
    }

    override fun findAccount(name: String): AuthAccount? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT id, name, role, password_hash
                FROM users
                WHERE lower(name) = lower(?)
                LIMIT 1
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, name)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) {
                        AuthAccount(
                            id = rs.getObject("id").toString(),
                            name = rs.getString("name"),
                            role = UserRole.valueOf(rs.getString("role")),
                            passwordHash = rs.getString("password_hash")
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    override fun findUser(userId: String): User? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT id, name, role FROM users WHERE id = ?"
            ).use { stmt ->
                stmt.setString(1, userId)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) {
                        User(
                            id = rs.getObject("id").toString(),
                            name = rs.getString("name"),
                            role = UserRole.valueOf(rs.getString("role"))
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    override fun findUserRole(userId: String): UserRole? {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT role FROM users WHERE id = ?").use { stmt ->
                stmt.setString(1, userId)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) UserRole.valueOf(rs.getString("role")) else null
                }
            }
        }
    }

    override fun findUserName(userId: String): String? {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT name FROM users WHERE id = ?").use { stmt ->
                stmt.setString(1, userId)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) rs.getString("name") else null
                }
            }
        }
    }

    override fun listUsers(): List<User> {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT id, name, role FROM users").use { stmt ->
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<User>()
                    while (rs.next()) {
                        result += User(
                            id = rs.getObject("id").toString(),
                            name = rs.getString("name"),
                            role = UserRole.valueOf(rs.getString("role"))
                        )
                    }
                    return result
                }
            }
        }
    }

    override fun activeQuests(): List<Quest> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT id, title, description, institution_name FROM quests WHERE is_active = TRUE ORDER BY created_at"
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<Quest>()
                    while (rs.next()) {
                        result += Quest(
                            id = rs.getObject("id").toString(),
                            title = rs.getString("title"),
                            description = rs.getString("description"),
                            institutionName = rs.getString("institution_name")
                        )
                    }
                    return result
                }
            }
        }
    }

    override fun tasksByQuest(questId: String): List<Task> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT t.id, t.location_id, t.title, t.description, t.task_type, t.max_score, t.media_url, t.media_type,
                       (SELECT option_text FROM task_options o WHERE o.task_id = t.id AND o.is_correct = TRUE LIMIT 1) AS correct_answer
                FROM tasks t
                JOIN locations l ON l.id = t.location_id
                WHERE l.quest_id = ?
                ORDER BY l.position, t.title
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, questId)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<Task>()
                    while (rs.next()) {
                        result += rs.toTask(connection)
                    }
                    return result
                }
            }
        }
    }

    override fun allQuests(): List<Quest> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT id, title, description, institution_name FROM quests ORDER BY created_at DESC"
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<Quest>()
                    while (rs.next()) {
                        result += Quest(
                            id = rs.getObject("id").toString(),
                            title = rs.getString("title"),
                            description = rs.getString("description"),
                            institutionName = rs.getString("institution_name")
                        )
                    }
                    return result
                }
            }
        }
    }

    override fun teacherQuests(teacherId: String): List<TeacherQuestSummaryRecord> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT q.id, q.title, q.description, q.institution_name, q.is_active,
                       q.owner_id, owner.name AS owner_name
                FROM quests q
                JOIN quest_teachers qt ON qt.quest_id = q.id
                LEFT JOIN users owner ON owner.id = q.owner_id
                WHERE qt.teacher_id = ?
                ORDER BY q.created_at DESC
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, teacherId)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<TeacherQuestSummaryRecord>()
                    while (rs.next()) {
                        result += TeacherQuestSummaryRecord(
                            questId = rs.getObject("id").toString(),
                            title = rs.getString("title"),
                            description = rs.getString("description"),
                            institutionName = rs.getString("institution_name"),
                            isActive = rs.getBoolean("is_active"),
                            ownerId = rs.getString("owner_id"),
                            ownerName = rs.getString("owner_name")
                        )
                    }
                    return result
                }
            }
        }
    }

    override fun hasTeacherQuestAccess(teacherId: String, questId: String): Boolean {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT 1
                FROM quest_teachers
                WHERE quest_id = ? AND teacher_id = ?
                LIMIT 1
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, questId)
                stmt.setString(2, teacherId)
                stmt.executeQuery().use { rs ->
                    return rs.next()
                }
            }
        }
    }

    override fun isQuestOwner(teacherId: String, questId: String): Boolean {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT 1
                FROM quests
                WHERE id = ? AND owner_id = ?
                LIMIT 1
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, questId)
                stmt.setString(2, teacherId)
                stmt.executeQuery().use { rs ->
                    return rs.next()
                }
            }
        }
    }

    override fun updateQuest(
        questId: String,
        title: String,
        description: String,
        institutionName: String,
        isActive: Boolean
    ): Boolean {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE quests
                SET title = ?, description = ?, institution_name = ?, is_active = ?
                WHERE id = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, title)
                stmt.setString(2, description)
                stmt.setString(3, institutionName)
                stmt.setBoolean(4, isActive)
                stmt.setString(5, questId)
                return stmt.executeUpdate() > 0
            }
        }
    }

    override fun deleteQuest(questId: String): Boolean {
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM quests WHERE id = ?").use { stmt ->
                stmt.setString(1, questId)
                return stmt.executeUpdate() > 0
            }
        }
    }

    override fun listQuestTeachers(questId: String): List<TeacherQuestTeacherRecord> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT u.id, u.name, CASE WHEN q.owner_id = u.id THEN TRUE ELSE FALSE END AS is_owner
                FROM quest_teachers qt
                JOIN users u ON u.id = qt.teacher_id
                JOIN quests q ON q.id = qt.quest_id
                WHERE qt.quest_id = ? AND u.role = 'TEACHER'
                ORDER BY is_owner DESC, lower(u.name)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, questId)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<TeacherQuestTeacherRecord>()
                    while (rs.next()) {
                        result += TeacherQuestTeacherRecord(
                            teacherId = rs.getObject("id").toString(),
                            teacherName = rs.getString("name"),
                            isOwner = rs.getBoolean("is_owner")
                        )
                    }
                    return result
                }
            }
        }
    }

    override fun addTeacherToQuest(questId: String, teacherId: String): Boolean {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO quest_teachers (quest_id, teacher_id)
                VALUES (?, ?)
                ON CONFLICT (quest_id, teacher_id) DO NOTHING
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, questId)
                stmt.setString(2, teacherId)
                return stmt.executeUpdate() > 0
            }
        }
    }

    override fun removeTeacherFromQuest(questId: String, teacherId: String): Boolean {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                DELETE FROM quest_teachers
                WHERE quest_id = ? AND teacher_id = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, questId)
                stmt.setString(2, teacherId)
                return stmt.executeUpdate() > 0
            }
        }
    }

    override fun findQuestIdByLocation(locationId: String): String? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT quest_id
                FROM locations
                WHERE id = ?
                LIMIT 1
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, locationId)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) rs.getObject("quest_id").toString() else null
                }
            }
        }
    }

    override fun findQuestIdByTask(taskId: String): String? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT l.quest_id
                FROM tasks t
                JOIN locations l ON l.id = t.location_id
                WHERE t.id = ?
                LIMIT 1
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, taskId)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) rs.getObject("quest_id").toString() else null
                }
            }
        }
    }

    override fun questLocations(questId: String): List<TeacherQuestLocationRecord> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT l.id, l.position, l.title, l.qr_code, COUNT(t.id) AS tasks_count
                FROM locations l
                LEFT JOIN tasks t ON t.location_id = l.id
                WHERE l.quest_id = ?
                GROUP BY l.id, l.position, l.title, l.qr_code
                ORDER BY l.position
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, questId)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<TeacherQuestLocationRecord>()
                    while (rs.next()) {
                        result += TeacherQuestLocationRecord(
                            locationId = rs.getObject("id").toString(),
                            position = rs.getInt("position"),
                            title = rs.getString("title"),
                            qrCode = rs.getString("qr_code"),
                            tasksCount = rs.getInt("tasks_count")
                        )
                    }
                    return result
                }
            }
        }
    }

    override fun tasksByLocation(locationId: String): List<Task> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT t.id, t.location_id, t.title, t.description, t.task_type, t.max_score, t.media_url, t.media_type,
                       (SELECT option_text FROM task_options o WHERE o.task_id = t.id AND o.is_correct = TRUE LIMIT 1) AS correct_answer
                FROM tasks t
                WHERE t.location_id = ?
                ORDER BY t.title
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, locationId)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<Task>()
                    while (rs.next()) {
                        result += rs.toTask(connection)
                    }
                    return result
                }
            }
        }
    }

    override fun locationTasks(locationId: String): List<TeacherTaskRecord> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT t.id, t.location_id, t.title, t.description, t.task_type, t.max_score, t.media_url, t.media_type,
                       (SELECT option_text FROM task_options o WHERE o.task_id = t.id AND o.is_correct = TRUE LIMIT 1) AS correct_answer
                FROM tasks t
                WHERE t.location_id = ?
                ORDER BY t.title
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, locationId)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<TeacherTaskRecord>()
                    while (rs.next()) {
                        val taskId = rs.getObject("id").toString()
                        val options = loadTaskOptions(connection, taskId)
                        result += TeacherTaskRecord(
                            taskId = taskId,
                            locationId = rs.getObject("location_id").toString(),
                            title = rs.getString("title"),
                            description = rs.getString("description"),
                            taskType = rs.getString("task_type"),
                            maxScore = rs.getInt("max_score"),
                            options = options.map { it.text },
                            correctOptionIndex = options.indexOfFirst { it.isCorrect }.takeIf { it >= 0 },
                            correctAnswer = rs.getString("correct_answer"),
                            mediaUrl = rs.getString("media_url"),
                            mediaType = rs.getString("media_type")
                        )
                    }
                    return result
                }
            }
        }
    }

    override fun findTask(taskId: String): Task? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT t.id, t.location_id, t.title, t.description, t.task_type, t.media_url, t.media_type,
                       (SELECT option_text FROM task_options o WHERE o.task_id = t.id AND o.is_correct = TRUE LIMIT 1) AS correct_answer
                FROM tasks t
                WHERE t.id = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, taskId)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) {
                        val options = loadTaskOptions(connection, taskId)
                        Task(
                            id = rs.getObject("id").toString(),
                            locationId = rs.getObject("location_id").toString(),
                            title = rs.getString("title"),
                            description = rs.getString("description"),
                            taskType = rs.getString("task_type"),
                            options = options,
                            mediaUrl = rs.getString("media_url"),
                            mediaType = rs.getString("media_type"),
                            code = rs.getObject("id").toString().take(8).uppercase(),
                            correctAnswer = rs.getString("correct_answer")
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    override fun findLocationByQr(qrCode: String): LocationScanPoint? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT quest_id, id, position FROM locations WHERE qr_code = ?"
            ).use { stmt ->
                stmt.setString(1, qrCode)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) {
                        LocationScanPoint(
                            questId = rs.getObject("quest_id").toString(),
                            locationId = rs.getObject("id").toString(),
                            order = rs.getInt("position")
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    override fun totalLocations(questId: String): Int {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT COUNT(*) AS cnt FROM locations WHERE quest_id = ?"
            ).use { stmt ->
                stmt.setString(1, questId)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) rs.getInt("cnt") else 0
                }
            }
        }
    }

    override fun scannedLocations(userId: String, questId: String): Set<String> {
        dataSource.connection.use { connection ->
            val progressId = ensureProgress(connection, userId, questId)
            connection.prepareStatement(
                "SELECT location_id FROM progress_steps WHERE progress_id = ? AND status = 'SCANNED'"
            ).use { stmt ->
                stmt.setString(1, progressId.toString())
                stmt.executeQuery().use { rs ->
                    val set = linkedSetOf<String>()
                    while (rs.next()) {
                        set += rs.getObject("location_id").toString()
                    }
                    return set
                }
            }
        }
    }

    override fun markLocationScanned(userId: String, questId: String, locationId: String) {
        dataSource.connection.use { connection ->
            val progressId = ensureProgress(connection, userId, questId)
            connection.prepareStatement(
                "SELECT 1 FROM progress_steps WHERE progress_id = ? AND location_id = ?"
            ).use { check ->
                check.setString(1, progressId.toString())
                check.setString(2, locationId)
                check.executeQuery().use { rs ->
                    if (rs.next()) return
                }
            }
            connection.prepareStatement(
                """
                INSERT INTO progress_steps (id, progress_id, location_id, status, scanned_at)
                VALUES (?, ?, ?, 'SCANNED', CURRENT_TIMESTAMP)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, UUID.randomUUID().toString())
                stmt.setString(2, progressId.toString())
                stmt.setString(3, locationId)
                stmt.executeUpdate()
            }
        }
    }

    override fun score(userId: String, questId: String): Int {
        dataSource.connection.use { connection ->
            val progressId = ensureProgress(connection, userId, questId)
            connection.prepareStatement("SELECT score FROM progress WHERE id = ?").use { stmt ->
                stmt.setString(1, progressId.toString())
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) rs.getInt("score") else 0
                }
            }
        }
    }

    override fun addScore(userId: String, questId: String, scoreDelta: Int) {
        dataSource.connection.use { connection ->
            val progressId = ensureProgress(connection, userId, questId)
            connection.prepareStatement("UPDATE progress SET score = score + ? WHERE id = ?").use { stmt ->
                stmt.setInt(1, scoreDelta)
                stmt.setString(2, progressId.toString())
                stmt.executeUpdate()
            }
        }
    }

    override fun hasSubmittedAnswer(userId: String, questId: String, taskId: String): Boolean {
        dataSource.connection.use { connection ->
            val progressId = ensureProgress(connection, userId, questId)
            connection.prepareStatement(
                """
                SELECT 1
                FROM answers
                WHERE progress_id = ? AND task_id = ?
                LIMIT 1
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, progressId.toString())
                stmt.setString(2, taskId)
                stmt.executeQuery().use { rs ->
                    return rs.next()
                }
            }
        }
    }

    override fun saveAnswer(
        userId: String,
        questId: String,
        taskId: String,
        answerText: String,
        isCorrect: Boolean,
        scoreAwarded: Int
    ) {
        dataSource.connection.use { connection ->
            val progressId = ensureProgress(connection, userId, questId)
            connection.prepareStatement(
                """
                INSERT INTO answers (id, progress_id, task_id, answer_text, is_correct, score_awarded)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, UUID.randomUUID().toString())
                stmt.setString(2, progressId.toString())
                stmt.setString(3, taskId)
                stmt.setString(4, answerText)
                stmt.setBoolean(5, isCorrect)
                stmt.setInt(6, scoreAwarded)
                stmt.executeUpdate()
            }
        }
    }

    override fun saveHint(teacherId: String, participantId: String, questId: String, hint: String, type: String) {
        dataSource.connection.use { connection ->
            val progressId = ensureProgress(connection, participantId, questId)
            connection.prepareStatement(
                """
                INSERT INTO teacher_hints (id, teacher_id, progress_id, hint_text, hint_type)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, UUID.randomUUID().toString())
                stmt.setString(2, teacherId)
                stmt.setString(3, progressId.toString())
                stmt.setString(4, hint)
                stmt.setString(5, type)
                stmt.executeUpdate()
            }
        }
    }

    override fun listHints(userId: String, questId: String): List<ApplicantHintRecord> {
        dataSource.connection.use { connection ->
            val progressId = ensureProgress(connection, userId, questId)
            connection.prepareStatement(
                """
                SELECT id, hint_text, created_at, viewed_at, hint_type
                FROM teacher_hints
                WHERE progress_id = ?
                ORDER BY created_at DESC
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, progressId.toString())
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<ApplicantHintRecord>()
                    while (rs.next()) {
                        result += ApplicantHintRecord(
                            id = rs.getObject("id").toString(),
                            hint = rs.getString("hint_text"),
                            createdAt = rs.getString("created_at"),
                            isRead = rs.getTimestamp("viewed_at") != null,
                            type = rs.getString("hint_type") ?: "HINT"
                        )
                    }
                    return result
                }
            }
        }
    }

    override fun latestRecommendation(userId: String, questId: String): String? {
        dataSource.connection.use { connection ->
            val progressId = ensureProgress(connection, userId, questId)
            connection.prepareStatement(
                """
                SELECT hint_text
                FROM teacher_hints
                WHERE progress_id = ? AND hint_type = 'RECOMMENDATION'
                ORDER BY created_at DESC
                LIMIT 1
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, progressId.toString())
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) rs.getString("hint_text") else null
                }
            }
        }
    }

    override fun participantAnswers(userId: String, questId: String): List<ParticipantAnswerRecord> {
        dataSource.connection.use { connection ->
            val progressId = ensureProgress(connection, userId, questId)
            connection.prepareStatement(
                """
                SELECT a.task_id, t.title AS task_title, l.title AS location_title, t.task_type,
                       a.answer_text, a.is_correct, a.created_at,
                       (
                         SELECT option_text
                         FROM task_options o
                         WHERE o.task_id = t.id AND o.is_correct = TRUE
                         LIMIT 1
                       ) AS expected_answer
                FROM answers a
                JOIN tasks t ON t.id = a.task_id
                JOIN locations l ON l.id = t.location_id
                WHERE a.progress_id = ?
                ORDER BY a.created_at DESC
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, progressId.toString())
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<ParticipantAnswerRecord>()
                    while (rs.next()) {
                        result += ParticipantAnswerRecord(
                            taskId = rs.getObject("task_id").toString(),
                            taskTitle = rs.getString("task_title"),
                            locationTitle = rs.getString("location_title"),
                            taskType = rs.getString("task_type"),
                            submittedAnswer = rs.getString("answer_text"),
                            expectedAnswer = rs.getString("expected_answer"),
                            isCorrect = rs.getBoolean("is_correct"),
                            createdAt = rs.getString("created_at")
                        )
                    }
                    return result
                }
            }
        }
    }

    override fun markHintsViewed(userId: String, questId: String) {
        dataSource.connection.use { connection ->
            val progressId = ensureProgress(connection, userId, questId)
            connection.prepareStatement(
                """
                UPDATE teacher_hints
                SET viewed_at = CURRENT_TIMESTAMP
                WHERE progress_id = ? AND viewed_at IS NULL
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, progressId.toString())
                stmt.executeUpdate()
            }
        }
    }

    override fun participantStats(userId: String, questId: String): TeacherParticipantStats {
        dataSource.connection.use { connection ->
            val progressId = ensureProgress(connection, userId, questId)
            val attempts = connection.prepareStatement(
                "SELECT COUNT(*) AS cnt FROM answers WHERE progress_id = ?"
            ).use { stmt ->
                stmt.setString(1, progressId.toString())
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt("cnt") else 0
                }
            }
            val wrong = connection.prepareStatement(
                "SELECT COUNT(*) AS cnt FROM answers WHERE progress_id = ? AND is_correct = FALSE"
            ).use { stmt ->
                stmt.setString(1, progressId.toString())
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt("cnt") else 0
                }
            }
            val lastActivity = connection.prepareStatement(
                """
                SELECT MAX(activity_time) AS last_activity
                FROM (
                    SELECT scanned_at AS activity_time FROM progress_steps WHERE progress_id = ?
                    UNION ALL
                    SELECT answered_at AS activity_time FROM progress_steps WHERE progress_id = ?
                    UNION ALL
                    SELECT created_at AS activity_time FROM answers WHERE progress_id = ?
                    UNION ALL
                    SELECT created_at AS activity_time FROM teacher_hints WHERE progress_id = ?
                ) activity
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, progressId.toString())
                stmt.setString(2, progressId.toString())
                stmt.setString(3, progressId.toString())
                stmt.setString(4, progressId.toString())
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getString("last_activity") else null
                }
            }
            return TeacherParticipantStats(
                attemptsCount = attempts,
                wrongAnswersCount = wrong,
                lastActivityAt = lastActivity
            )
        }
    }

    override fun createQuest(ownerId: String, title: String, description: String, institutionName: String, isActive: Boolean): String {
        val questId = UUID.randomUUID().toString()
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    """
                    INSERT INTO quests (id, title, description, institution_name, is_active, owner_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setString(1, questId)
                    stmt.setString(2, title)
                    stmt.setString(3, description)
                    stmt.setString(4, institutionName)
                    stmt.setBoolean(5, isActive)
                    stmt.setString(6, ownerId)
                    stmt.executeUpdate()
                }

                connection.prepareStatement(
                    """
                    INSERT INTO quest_teachers (quest_id, teacher_id)
                    VALUES (?, ?)
                    ON CONFLICT (quest_id, teacher_id) DO NOTHING
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setString(1, questId)
                    stmt.setString(2, ownerId)
                    stmt.executeUpdate()
                }

                connection.commit()
            } catch (t: Throwable) {
                connection.rollback()
                throw t
            } finally {
                connection.autoCommit = true
            }
        }
        return questId
    }

    override fun createLocation(questId: String, position: Int, title: String, qrCode: String): String {
        val locationId = UUID.randomUUID().toString()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO locations (id, quest_id, position, title, qr_code)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, locationId)
                stmt.setString(2, questId)
                stmt.setInt(3, position)
                stmt.setString(4, title)
                stmt.setString(5, qrCode)
                stmt.executeUpdate()
            }
        }
        return locationId
    }

    override fun createTask(
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
        val taskId = UUID.randomUUID().toString()
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    """
                    INSERT INTO tasks (id, location_id, title, description, task_type, max_score, media_url, media_type)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setString(1, taskId)
                    stmt.setString(2, locationId)
                    stmt.setString(3, title)
                    stmt.setString(4, description)
                    stmt.setString(5, taskType)
                    stmt.setInt(6, maxScore)
                    stmt.setString(7, mediaUrl)
                    stmt.setString(8, mediaType)
                    stmt.executeUpdate()
                }

                insertTaskOptions(connection, taskId, options, correctOptionIndex, correctAnswer)
                connection.commit()
            } catch (t: Throwable) {
                connection.rollback()
                throw t
            } finally {
                connection.autoCommit = true
            }
        }
        return taskId
    }

    override fun updateTask(
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
    ): Boolean {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val updatedRows = connection.prepareStatement(
                    """
                    UPDATE tasks
                    SET title = ?, description = ?, task_type = ?, max_score = ?, media_url = ?, media_type = ?
                    WHERE id = ?
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setString(1, title)
                    stmt.setString(2, description)
                    stmt.setString(3, taskType)
                    stmt.setInt(4, maxScore)
                    stmt.setString(5, mediaUrl)
                    stmt.setString(6, mediaType)
                    stmt.setString(7, taskId)
                    stmt.executeUpdate()
                }
                if (updatedRows == 0) {
                    connection.rollback()
                    return false
                }

                connection.prepareStatement(
                    "DELETE FROM task_options WHERE task_id = ?"
                ).use { stmt ->
                    stmt.setString(1, taskId)
                    stmt.executeUpdate()
                }

                insertTaskOptions(connection, taskId, options, correctOptionIndex, correctAnswer)

                connection.commit()
                return true
            } catch (t: Throwable) {
                connection.rollback()
                throw t
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun deleteTask(taskId: String): Boolean {
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM tasks WHERE id = ?").use { stmt ->
                stmt.setString(1, taskId)
                return stmt.executeUpdate() > 0
            }
        }
    }

    private fun ensureProgress(connection: java.sql.Connection, userId: String, questId: String): UUID {
        connection.prepareStatement(
            "SELECT id FROM progress WHERE user_id = ? AND quest_id = ?"
        ).use { stmt ->
            stmt.setString(1, userId)
            stmt.setString(2, questId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) return UUID.fromString(rs.getString("id"))
            }
        }

        val progressId = UUID.randomUUID()
        connection.prepareStatement(
            """
            INSERT INTO progress (id, user_id, quest_id, score, is_completed)
            VALUES (?, ?, ?, 0, FALSE)
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, progressId.toString())
            stmt.setString(2, userId)
            stmt.setString(3, questId)
            stmt.executeUpdate()
        }
        return progressId
    }

    private fun loadTaskOptions(connection: Connection, taskId: String): List<TaskOption> {
        connection.prepareStatement(
            """
            SELECT option_text, is_correct
            FROM task_options
            WHERE task_id = ?
            ORDER BY id
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, taskId)
            stmt.executeQuery().use { rs ->
                val options = mutableListOf<TaskOption>()
                while (rs.next()) {
                    options += TaskOption(
                        text = rs.getString("option_text"),
                        isCorrect = rs.getBoolean("is_correct")
                    )
                }
                return options
            }
        }
    }

    private fun insertTaskOptions(
        connection: Connection,
        taskId: String,
        options: List<String>,
        correctOptionIndex: Int?,
        correctAnswer: String?
    ) {
        val normalizedOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
        if (normalizedOptions.isNotEmpty()) {
            connection.prepareStatement(
                """
                INSERT INTO task_options (id, task_id, option_text, is_correct)
                VALUES (?, ?, ?, ?)
                """.trimIndent()
            ).use { stmt ->
                normalizedOptions.forEachIndexed { index, optionText ->
                    stmt.setString(1, UUID.randomUUID().toString())
                    stmt.setString(2, taskId)
                    stmt.setString(3, optionText)
                    stmt.setBoolean(4, correctOptionIndex == index)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            return
        }

        if (!correctAnswer.isNullOrBlank()) {
            connection.prepareStatement(
                """
                INSERT INTO task_options (id, task_id, option_text, is_correct)
                VALUES (?, ?, ?, TRUE)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, UUID.randomUUID().toString())
                stmt.setString(2, taskId)
                stmt.setString(3, correctAnswer)
                stmt.executeUpdate()
            }
        }
    }

    private fun ResultSet.toTask(connection: Connection): Task {
        val id = getObject("id").toString()
        val options = loadTaskOptions(connection, id)
        return Task(
            id = id,
            locationId = getObject("location_id").toString(),
            title = getString("title"),
            description = getString("description"),
            taskType = getString("task_type"),
            options = options,
            mediaUrl = getString("media_url"),
            mediaType = getString("media_type"),
            code = id.take(8).uppercase(),
            correctAnswer = getString("correct_answer")
        )
    }
}
