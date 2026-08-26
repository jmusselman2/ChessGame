package com.jmussel.chessgame.server.db

import javax.sql.DataSource
import kotlin.test.assertTrue

/**
 * Integration tests run against the real disposable PostgreSQL from
 * `docs/DEVELOPMENT.md`, because the behaviour worth testing — constraints, transactions,
 * isolation — is PostgreSQL's, not a stand-in's.
 *
 * When `TEST_DATABASE_URL` is not set the database is simply not available on that
 * machine, and these tests report themselves as skipped rather than failing. CI sets the
 * variable, so they always run there.
 */
object DatabaseTestSupport {
    /** The configured test database, or `null` when this machine has none. */
    val config: DatabaseConfig? = DatabaseConfig.fromEnvironmentOrNull(DatabaseConfig.TEST_DATABASE_URL)

    val isAvailable: Boolean
        get() = config != null

    /**
     * Runs [block] against a freshly migrated test database, closing the pool afterwards.
     *
     * Does nothing when no test database is configured.
     */
    fun withMigratedDatabase(block: (DataSource) -> Unit) =
        withDatabase { dataSource ->
            Migrations.reset(dataSource)
            block(dataSource)
        }

    /**
     * Runs [block] against an emptied test database with no migrations applied.
     *
     * Does nothing when no test database is configured.
     */
    fun withEmptyDatabase(block: (DataSource) -> Unit) =
        withDatabase { dataSource ->
            clean(dataSource)
            block(dataSource)
        }

    /** Whether [table] exists in the current schema. */
    fun tableExists(
        dataSource: DataSource,
        table: String,
    ): Boolean =
        dataSource.connection.use { connection ->
            connection.prepareStatement("select to_regclass(?) is not null").use { statement ->
                statement.setString(1, table)
                statement.executeQuery().use { rows ->
                    rows.next() && rows.getBoolean(1)
                }
            }
        }

    /** Asserts the test database is reachable, so a misconfigured URL is not mistaken for absence. */
    fun assertReachable() =
        withDatabase { dataSource ->
            dataSource.connection.use { connection ->
                assertTrue(connection.isValid(VALIDATION_TIMEOUT_SECONDS))
            }
        }

    private const val VALIDATION_TIMEOUT_SECONDS = 5

    private fun clean(dataSource: DataSource) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("drop schema if exists public cascade")
                statement.execute("create schema public")
            }
            connection.commit()
        }
    }

    private fun withDatabase(block: (DataSource) -> Unit) {
        val dataSource = config?.dataSource() ?: return
        try {
            block(dataSource)
        } finally {
            (dataSource as? AutoCloseable)?.close()
        }
    }
}
