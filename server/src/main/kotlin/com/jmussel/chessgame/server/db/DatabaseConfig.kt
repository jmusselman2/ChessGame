package com.jmussel.chessgame.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.sql.DataSource

/**
 * Where the database is and how to reach it.
 *
 * Read from the environment so nothing about a real deployment is committed. The local
 * development values live in `.env.example`; see `docs/DEVELOPMENT.md`.
 */
data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val maximumPoolSize: Int = DEFAULT_POOL_SIZE,
) {
    companion object {
        const val DEFAULT_POOL_SIZE: Int = 5

        /** The variable holding the development database URL. */
        const val DATABASE_URL: String = "DATABASE_URL"

        /** The variable holding the disposable test database URL. */
        const val TEST_DATABASE_URL: String = "TEST_DATABASE_URL"

        /**
         * Reads a config from a `postgresql://user:password@host:port/database` URL, the
         * form `.env.example` and every hosting provider use.
         *
         * Any query string is carried through onto the JDBC URL, because that is where
         * connection properties live: a managed database is reached with `?sslmode=require`
         * or similar, and dropping it would quietly downgrade the connection (`M15.3`).
         *
         * The user info is percent-decoded, since a generated database password routinely
         * contains characters that have to be escaped in a URL.
         */
        fun fromUrl(url: String): DatabaseConfig {
            val uri = URI(url)
            require(uri.scheme == "postgresql" || uri.scheme == "postgres") {
                "Not a PostgreSQL URL: ${redact(url)}"
            }
            requireNotNull(uri.host) { "PostgreSQL URL has no host: ${redact(url)}" }

            val userInfo = uri.rawUserInfo.orEmpty().split(':', limit = 2)
            val port = if (uri.port == -1) DEFAULT_PORT else uri.port
            val query =
                uri.rawQuery
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "?$it" }
                    .orEmpty()

            return DatabaseConfig(
                jdbcUrl = "jdbc:postgresql://${uri.host}:$port${uri.path}$query",
                username = decode(userInfo.getOrElse(0) { "" }),
                password = decode(userInfo.getOrElse(1) { "" }),
            )
        }

        private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)

        /** [url] with any user info removed, so a failure message cannot carry a password. */
        private fun redact(url: String): String = url.replace(USER_INFO, "//<credentials>@")

        private val USER_INFO = Regex("//[^/@]*@")

        /** Reads the config from [variable], or returns `null` when it is not set. */
        fun fromEnvironmentOrNull(variable: String = DATABASE_URL): DatabaseConfig? =
            System.getenv(variable)?.takeIf { it.isNotBlank() }?.let(::fromUrl)

        /** Reads the config from [variable], failing when it is not set. */
        fun fromEnvironment(variable: String = DATABASE_URL): DatabaseConfig =
            requireNotNull(fromEnvironmentOrNull(variable)) { "$variable is not set" }

        private const val DEFAULT_PORT = 5432
    }

    /** A pooled [DataSource] for this configuration. The caller closes it. */
    fun dataSource(): DataSource =
        HikariDataSource(
            HikariConfig().also {
                it.jdbcUrl = jdbcUrl
                it.username = username
                it.password = password
                it.maximumPoolSize = maximumPoolSize
                it.isAutoCommit = false
            },
        )

    /** Never prints the password. */
    override fun toString(): String = "DatabaseConfig(jdbcUrl=$jdbcUrl, username=$username)"
}
