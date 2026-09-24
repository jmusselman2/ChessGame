package com.jmussel.chessgame.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration
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
    val timeouts: ConnectionTimeouts = ConnectionTimeouts(),
) {
    companion object {
        const val DEFAULT_POOL_SIZE: Int = 5

        /** Connections Flyway may hold at once while migrating. */
        const val MIGRATION_POOL_SIZE: Int = 3

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

    /**
     * A pooled [DataSource] for this configuration, whose every connection carries [timeouts].
     * The caller closes it.
     *
     * The timeouts are set on each connection as it opens, not on the database role: the
     * server connects as `postgres`, which is also the Supabase dashboard's role, and a
     * `SET` holds for the whole session through Supabase's session pooler (`D077`).
     */
    fun dataSource(): DataSource =
        HikariDataSource(
            baseConfig().also {
                it.maximumPoolSize = maximumPoolSize
                it.connectionInitSql = timeouts.initSql
            },
        )

    /**
     * A small pool for Flyway, with no timeouts. The caller closes it.
     *
     * A migration may rightly run longer, or wait on a lock longer, than any request should,
     * so it gets connections of its own rather than ones from [dataSource] with their
     * timeouts switched off, which would go back into the pool that way (`D077`). Flyway holds
     * more than one connection at a time, so a pool of one would wait on itself.
     */
    fun migrationDataSource(): HikariDataSource = HikariDataSource(baseConfig().also { it.maximumPoolSize = MIGRATION_POOL_SIZE })

    private fun baseConfig(): HikariConfig =
        HikariConfig().also {
            it.jdbcUrl = jdbcUrl
            it.username = username
            it.password = password
            it.isAutoCommit = false
        }

    /** Never prints the password. */
    override fun toString(): String = "DatabaseConfig(jdbcUrl=$jdbcUrl, username=$username)"
}

/**
 * How long a request's connection may spend on one statement, idle inside a transaction, or
 * waiting for a lock before PostgreSQL gives up on it (`D077`).
 *
 * Without them a request that hangs inside a transaction keeps whatever it locked, a game
 * row taken `FOR UPDATE` included, until the connection dies. Every query the server runs
 * takes about a millisecond (`M17.12`), so each limit only ever ends something that is stuck.
 */
data class ConnectionTimeouts(
    val statement: Duration = Duration.ofSeconds(30),
    val idleInTransaction: Duration = Duration.ofSeconds(60),
    val lock: Duration = Duration.ofSeconds(10),
) {
    /** The `SET`s that apply these to a connection, run once as it opens. */
    val initSql: String
        get() =
            "SET statement_timeout = ${statement.toMillis()}; " +
                "SET idle_in_transaction_session_timeout = ${idleInTransaction.toMillis()}; " +
                "SET lock_timeout = ${lock.toMillis()}"
}
