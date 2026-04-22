package dev.pan4ur.abitour.server.db

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction

object UsersTable : Table("users") {
    val id = varchar("id", 36)
    val name = text("name")
    val role = varchar("role", 16)
    val passwordHash = text("password_hash").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

object QuestsTable : Table("quests") {
    val id = varchar("id", 36)
    val title = text("title")
    val description = text("description")
    val isActive = bool("is_active").default(true)
    val institutionName = text("institution_name").nullable()
    val ownerId = optReference("owner_id", UsersTable.id, onDelete = ReferenceOption.SET_NULL)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, isActive)
        index(false, ownerId)
    }
}

object LocationsTable : Table("locations") {
    val id = varchar("id", 36)
    val questId = varchar("quest_id", 36).references(QuestsTable.id, onDelete = ReferenceOption.CASCADE)
    val position = integer("position")
    val title = text("title")
    val qrCode = text("qr_code")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(questId, position)
        index(false, questId)
    }
}

object TasksTable : Table("tasks") {
    val id = varchar("id", 36)
    val locationId = varchar("location_id", 36).references(LocationsTable.id, onDelete = ReferenceOption.CASCADE)
    val title = text("title")
    val description = text("description")
    val taskType = varchar("task_type", 32)
    val maxScore = integer("max_score").default(1)
    val mediaUrl = text("media_url").nullable()
    val mediaType = varchar("media_type", 32).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, locationId)
    }
}

object TaskOptionsTable : Table("task_options") {
    val id = varchar("id", 36)
    val taskId = varchar("task_id", 36).references(TasksTable.id, onDelete = ReferenceOption.CASCADE)
    val optionText = text("option_text")
    val isCorrect = bool("is_correct").default(false)

    override val primaryKey = PrimaryKey(id)
}

object ProgressTable : Table("progress") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val questId = varchar("quest_id", 36).references(QuestsTable.id, onDelete = ReferenceOption.CASCADE)
    val score = integer("score").default(0)
    val isCompleted = bool("is_completed").default(false)
    val startedAt = timestamp("started_at").defaultExpression(CurrentTimestamp)
    val completedAt = timestamp("completed_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(userId, questId)
        index(false, userId)
        index(false, questId)
    }
}

object ProgressStepsTable : Table("progress_steps") {
    val id = varchar("id", 36)
    val progressId = varchar("progress_id", 36).references(ProgressTable.id, onDelete = ReferenceOption.CASCADE)
    val locationId = varchar("location_id", 36).references(LocationsTable.id, onDelete = ReferenceOption.CASCADE)
    val status = varchar("status", 32)
    val scannedAt = timestamp("scanned_at").nullable()
    val answeredAt = timestamp("answered_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object AnswersTable : Table("answers") {
    val id = varchar("id", 36)
    val progressId = varchar("progress_id", 36).references(ProgressTable.id, onDelete = ReferenceOption.CASCADE)
    val taskId = varchar("task_id", 36).references(TasksTable.id, onDelete = ReferenceOption.CASCADE)
    val answerText = text("answer_text")
    val isCorrect = bool("is_correct")
    val scoreAwarded = integer("score_awarded").default(0)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

object TeacherHintsTable : Table("teacher_hints") {
    val id = varchar("id", 36)
    val teacherId = varchar("teacher_id", 36).references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val progressId = varchar("progress_id", 36).references(ProgressTable.id, onDelete = ReferenceOption.CASCADE)
    val hintText = text("hint_text")
    val hintType = varchar("hint_type", 32).default("HINT")
    val viewedAt = timestamp("viewed_at").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

object QuestTeachersTable : Table("quest_teachers") {
    val questId = varchar("quest_id", 36).references(QuestsTable.id, onDelete = ReferenceOption.CASCADE)
    val teacherId = varchar("teacher_id", 36).references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(questId, teacherId)

    init {
        index(false, questId)
        index(false, teacherId)
    }
}

internal fun initSchema() {
    transaction {
        SchemaUtils.createMissingTablesAndColumns(
            UsersTable,
            QuestsTable,
            LocationsTable,
            TasksTable,
            TaskOptionsTable,
            ProgressTable,
            ProgressStepsTable,
            AnswersTable,
            TeacherHintsTable,
            QuestTeachersTable
        )

        exec("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_name_role_unique ON users (name COLLATE NOCASE, role)")
    }
}
