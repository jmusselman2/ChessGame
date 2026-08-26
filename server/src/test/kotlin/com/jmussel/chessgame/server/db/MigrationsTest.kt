package com.jmussel.chessgame.server.db

import kotlin.test.Test
import kotlin.test.assertEquals
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
