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

    /** Migrates [dataSource] and returns Exposed's handle on it, in that order. */
    fun connectAndMigrate(dataSource: DataSource): Database {
        Migrations.migrate(dataSource)
        return connect(dataSource)
    }
}
