package dev.pan4ur.abitour.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.pan4ur.abitour.server.config.DatabaseConfig
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)
    private var initialized = false
    var dataSource: HikariDataSource? = null
        private set

    val isReady: Boolean
        get() = initialized && dataSource != null

    fun init(config: DatabaseConfig) {
        if (initialized) return

        try {
            val jdbcUrl = normalizeSqliteJdbcUrl(config.jdbcUrl)
            val isSqlite = jdbcUrl.startsWith("jdbc:sqlite:")
            if (isSqlite) {
                ensureSqliteParentDirectory(jdbcUrl)
                logger.info("Using SQLite database at {}", jdbcUrl.removePrefix("jdbc:sqlite:"))
            }
            val hikariConfig = HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                username = config.user
                password = config.password
                maximumPoolSize = config.maxPoolSize
                driverClassName = if (isSqlite) "org.sqlite.JDBC" else "org.postgresql.Driver"
                connectionInitSql = if (isSqlite) "PRAGMA foreign_keys=ON;" else "SET client_encoding TO 'UTF8'"
                isAutoCommit = true
                validate()
            }

            val source = HikariDataSource(hikariConfig)
            Database.connect(source)
            initSchema()

            dataSource = source
            initialized = true
            logger.info("Database initialized and schema ensured")
        } catch (error: Throwable) {
            logger.error("Database init failed, server cannot continue without persistent storage", error)
            throw IllegalStateException("Failed to initialize database", error)
        }
    }

    private fun normalizeSqliteJdbcUrl(rawJdbcUrl: String): String {
        if (!rawJdbcUrl.startsWith("jdbc:sqlite:")) return rawJdbcUrl
        val rawPath = rawJdbcUrl.removePrefix("jdbc:sqlite:")
        if (rawPath == ":memory:") return rawJdbcUrl

        val path = Paths.get(rawPath)
        if (path.isAbsolute) return rawJdbcUrl

        val envPath = System.getenv("ABITOUR_DB_PATH")?.trim().orEmpty()
        if (envPath.isNotEmpty()) {
            val envResolved = Paths.get(envPath).toAbsolutePath().normalize()
            return "jdbc:sqlite:${envResolved.toString().replace('\\', '/')}"
        }

        val projectRoot = findProjectRoot(Paths.get(System.getProperty("user.dir")))
        val resolved = projectRoot.resolve(path).normalize().toAbsolutePath()
        return "jdbc:sqlite:${resolved.toString().replace('\\', '/')}"
    }

    private fun findProjectRoot(startDir: Path): Path {
        var current: Path? = startDir.toAbsolutePath().normalize()
        while (current != null) {
            val hasSettings = current.resolve("settings.gradle.kts").toFile().exists()
            val hasServerModule = current.resolve("server").toFile().isDirectory
            if (hasSettings && hasServerModule) return current
            current = current.parent
        }
        return startDir.toAbsolutePath().normalize()
    }

    private fun ensureSqliteParentDirectory(jdbcUrl: String) {
        val path = jdbcUrl.removePrefix("jdbc:sqlite:")
        if (path == ":memory:") return
        val file = File(path)
        val parent = file.parentFile ?: return
        if (!parent.exists()) {
            parent.mkdirs()
        }
    }
}
