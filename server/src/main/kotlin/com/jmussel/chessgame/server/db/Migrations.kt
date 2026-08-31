package com.jmussel.chessgame.server.db

import org.flywaydb.core.Flyway
import javax.sql.DataSource

/**
 * Applying the SQL migrations in `database/migrations/`.
 *
 * The files are plain, forward-only SQL and are the source of truth for the schema —
 * nothing generates them from Kotlin. Flyway records what it has applied in
 * [HISTORY_TABLE], so running [migrate] again on an up-to-date database does nothing:
 * the same command is safe on a fresh database, a half-migrated one, and a current one.
 *
 * The build copies `database/migrations/` onto the classpath at [LOCATION], so the server
 * and the tests migrate from exactly the same files.
 */
object Migrations {
    /** Where the migration files sit on the classpath. */
    const val LOCATION: String = "classpath:db/migration"

    /** Flyway's own bookkeeping table. */
    const val HISTORY_TABLE: String = "flyway_schema_history"

    /**
     * Applies every migration [dataSource] has not seen, and returns how many were applied.
     */
    fun migrate(dataSource: DataSource): Int = flywayFor(dataSource).migrate().migrationsExecuted

    /** The versions already applied to [dataSource], oldest first. */
    fun appliedVersions(dataSource: DataSource): List<String> =
        flywayFor(dataSource)
            .info()
            .applied()
            .mapNotNull { it.version?.version }

    /**
     * Drops everything in the database and re-applies every migration.
     *
     * Refuses unless the database is disposable (see [DisposableDatabase]), and asks the
     * live connection where it is actually connected rather than trusting a caller to say
     * — the whole point is to catch a `TEST_DATABASE_URL` pointing somewhere it should
     * not. The refusal happens before anything is dropped.
     */
    fun reset(dataSource: DataSource): Int {
        DisposableDatabase.requireDisposable(urlOf(dataSource))

        val flyway = flywayFor(dataSource, cleanDisabled = false)
        flyway.clean()
        return flyway.migrate().migrationsExecuted
    }

    /** Where [dataSource] is really connected, as the JDBC driver reports it. */
    fun urlOf(dataSource: DataSource): String = dataSource.connection.use { it.metaData.url.orEmpty() }

    private fun flywayFor(
        dataSource: DataSource,
        cleanDisabled: Boolean = true,
    ): Flyway =
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations(LOCATION)
            .table(HISTORY_TABLE)
            .cleanDisabled(cleanDisabled)
            .load()
}
