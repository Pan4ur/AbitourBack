package dev.pan4ur.abitour.server.repository

import dev.pan4ur.abitour.server.db.DatabaseFactory
import org.slf4j.LoggerFactory

object RepositoryFactory {
    private val logger = LoggerFactory.getLogger(RepositoryFactory::class.java)

    fun create(): BackendRepository {
        val dataSource = DatabaseFactory.dataSource
        if (DatabaseFactory.isReady && dataSource != null) {
            logger.info("Using JDBC repository")
            return JdbcBackendRepository(dataSource)
        }
        throw IllegalStateException("Database is not initialized, refusing to use in-memory fallback")
    }
}
