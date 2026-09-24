package com.jmussel.chessgame.server.db

import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource

/**
 * Connecting Exposed to a pooled [DataSource].
 *
 * Nothing here creates or alters a schema — the SQL migrations in
 * `database/migrations/` are the source of truth.
 */
object Databases {
    /** Exposed's handle on [dataSource]. */
    fun connect(dataSource: DataSource): Database = Database.connect(dataSource)

    /**
     * Migrates [config]'s database, then returns Exposed's handle on a pool for requests.
     *
     * The migration runs on a connection of its own with no timeouts, which is closed before
     * the pool opens (`D077`).
     */
    fun connectAndMigrate(config: DatabaseConfig): Database {
        config.migrationDataSource().use { Migrations.migrate(it) }
        return connect(config.dataSource())
    }
}
