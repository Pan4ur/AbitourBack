package dev.pan4ur.abitour.server.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val jwt: JwtConfig,
    val database: DatabaseConfig
) {
    companion object {
        fun from(config: ApplicationConfig): AppConfig {
            val jwtConfig = JwtConfig(
                issuer = config.property("abitour.jwt.issuer").getString(),
                audience = config.property("abitour.jwt.audience").getString(),
                realm = config.property("abitour.jwt.realm").getString(),
                secret = envOrConfig("ABITOUR_JWT_SECRET", config.property("abitour.jwt.secret").getString()),
                accessTokenTtlMinutes = config.property("abitour.jwt.accessTokenTtlMinutes").getString().toLong()
            )
            val dbConfig = DatabaseConfig(
                jdbcUrl = envOrConfig("ABITOUR_DB_JDBC_URL", config.property("abitour.db.jdbcUrl").getString()),
                user = envOrConfig("ABITOUR_DB_USER", config.property("abitour.db.user").getString()),
                password = envOrConfig("ABITOUR_DB_PASSWORD", config.property("abitour.db.password").getString()),
                maxPoolSize = envOrConfig("ABITOUR_DB_MAX_POOL_SIZE", config.property("abitour.db.maxPoolSize").getString()).toInt()
            )
            return AppConfig(jwt = jwtConfig, database = dbConfig)
        }

        private fun envOrConfig(name: String, fallback: String): String {
            return System.getenv(name) ?: fallback
        }
    }
}

data class JwtConfig(
    val issuer: String,
    val audience: String,
    val realm: String,
    val secret: String,
    val accessTokenTtlMinutes: Long
)

data class DatabaseConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int
)
