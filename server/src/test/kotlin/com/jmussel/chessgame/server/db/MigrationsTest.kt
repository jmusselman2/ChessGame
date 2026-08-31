package com.jmussel.chessgame.server.db

import java.sql.Connection
import java.sql.DatabaseMetaData
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The migration process itself: it runs on an empty database, records what it applied, and
 * can be run again safely.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class MigrationsTest {
    @Test
    fun theTestDatabaseIsReachableWhenConfigured() {
        if (!DatabaseTestSupport.isAvailable) return

        DatabaseTestSupport.assertReachable()
    }

    @Test
    fun migratingAnEmptyDatabaseRecordsTheHistory() {
        DatabaseTestSupport.withEmptyDatabase { dataSource ->
            Migrations.migrate(dataSource)

            assertTrue(
                DatabaseTestSupport.tableExists(dataSource, Migrations.HISTORY_TABLE),
                "Flyway should have created ${Migrations.HISTORY_TABLE}",
            )
        }
    }

    @Test
    fun migratingIsRepeatable() {
        DatabaseTestSupport.withEmptyDatabase { dataSource ->
            val firstRun = Migrations.migrate(dataSource)
            val versionsAfterFirstRun = Migrations.appliedVersions(dataSource)

            val secondRun = Migrations.migrate(dataSource)

            assertEquals(0, secondRun, "an up-to-date database needs no further migration")
            assertEquals(versionsAfterFirstRun, Migrations.appliedVersions(dataSource))
            assertTrue(firstRun >= 0)
        }
    }

    @Test
    fun everyMigrationInTheRepositoryApplies() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val applied = Migrations.appliedVersions(dataSource)

            assertEquals(
                applied.size,
                applied.toSet().size,
                "two migrations share a version number",
            )
            assertEquals(applied.sortedWith(VERSION_ORDER), applied, "migrations applied out of order")
        }
    }

    @Test
    fun resettingReappliesEverythingFromScratch() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val afterFirstMigration = Migrations.appliedVersions(dataSource)

            Migrations.reset(dataSource)

            assertEquals(afterFirstMigration, Migrations.appliedVersions(dataSource))
            assertTrue(DatabaseTestSupport.tableExists(dataSource, Migrations.HISTORY_TABLE))
        }
    }

    /**
     * The guard from `M15.5`, exercised against the real destructive path.
     *
     * The database is the disposable local one throughout — only what the driver *reports*
     * about it is changed, which is exactly the mistake the guard exists to catch: a
     * `TEST_DATABASE_URL` naming the shared Supabase project (`D035`). The migrated schema
     * has to survive.
     */
    @Test
    fun resettingIsRefusedWhenTheDatabaseIsNotDisposableAndNothingIsDropped() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val beforehand = Migrations.appliedVersions(dataSource)
            assertTrue(beforehand.isNotEmpty(), "the fixture should start migrated")

            val pretendingToBeTheBeta =
                RelabelledDataSource(dataSource, "jdbc:postgresql://aws-0-us-east-2.pooler.supabase.com:5432/postgres")

            val refusal =
                assertFailsWith<NotADisposableDatabaseException> {
                    Migrations.reset(pretendingToBeTheBeta)
                }
            assertTrue(
                refusal.message.contains("pooler.supabase.com"),
                "the refusal should name the host it refused: ${refusal.message}",
            )

            assertEquals(
                beforehand,
                Migrations.appliedVersions(dataSource),
                "the refusal must happen before anything is dropped",
            )
            assertTrue(
                DatabaseTestSupport.tableExists(dataSource, "users"),
                "the refusal must happen before anything is dropped",
            )
        }
    }

    /** A [DataSource] that is the real one, except for the address it admits to. */
    private class RelabelledDataSource(
        private val real: DataSource,
        private val url: String,
    ) : DataSource by real {
        override fun getConnection(): Connection = RelabelledConnection(real.connection, url)
    }

    private class RelabelledConnection(
        private val real: Connection,
        private val url: String,
    ) : Connection by real {
        override fun getMetaData(): DatabaseMetaData = RelabelledMetaData(real.metaData, url)
    }

    private class RelabelledMetaData(
        real: DatabaseMetaData,
        private val url: String,
    ) : DatabaseMetaData by real {
        override fun getURL(): String = url
    }

    private companion object {
        /** Orders `1`, `2`, `10` numerically rather than as text. */
        val VERSION_ORDER: Comparator<String> =
            Comparator { left, right ->
                val leftParts = left.split('.').map { it.toIntOrNull() ?: 0 }
                val rightParts = right.split('.').map { it.toIntOrNull() ?: 0 }
                val size = maxOf(leftParts.size, rightParts.size)
                (0 until size)
                    .map { leftParts.getOrElse(it) { 0 }.compareTo(rightParts.getOrElse(it) { 0 }) }
                    .firstOrNull { it != 0 } ?: 0
            }
    }
}
