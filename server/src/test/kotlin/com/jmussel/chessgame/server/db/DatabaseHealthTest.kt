package com.jmussel.chessgame.server.db

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseHealthTest {
    @Test
    fun aHealthyCheckRunsTheThreeFixedStatementsInOrder() {
        val statements = mutableListOf<String>()
        val health =
            DatabaseHealth { sql ->
                statements += sql
                !sql.contains("FROM public.games") && !sql.contains("FROM public.game_series")
            }

        assertEquals(DatabaseHealthResult.Healthy, health.check())
        assertEquals(3, statements.size)
        assertEquals(true, statements[0].contains("FROM public.flyway_schema_history"))
        assertEquals(true, statements[0].contains("WHERE success = true"))
        assertEquals(true, statements[1].contains("FROM public.games"))
        assertEquals(true, statements[2].contains("FROM public.game_series"))
    }

    @Test
    fun aFalseSuccessfulMigrationResultStopsTheCheck() {
        val statements = mutableListOf<String>()
        val health = DatabaseHealth { sql -> statements.add(sql).let { false } }

        assertEquals(DatabaseHealthResult.Unavailable, health.check())
        assertEquals(1, statements.size)
    }

    @Test
    fun aFailureLogContainsOnlySafeClassification() {
        val logger = LoggerFactory.getLogger(DatabaseHealth::class.java) as Logger
        val captured = ListAppender<ILoggingEvent>().apply { start() }
        val secret = "jdbc:postgresql://secret-user:secret-password@example/database"
        val sql = "SELECT secret FROM private_table"

        logger.addAppender(captured)
        try {
            val health = DatabaseHealth { throw SQLException("$secret $sql", "42P01") }

            assertEquals(DatabaseHealthResult.Unavailable, health.check())
            val text = captured.list.joinToString("\n") { it.formattedMessage }
            assertTrue(
                text.matches(
                    Regex(
                        "Database health check=successful_migration result=unavailable " +
                            "durationMs=\\d+ category=missing_table",
                    ),
                ),
                text,
            )
            assertFalse(text.contains(secret))
            assertFalse(text.contains(sql))
        } finally {
            logger.detachAppender(captured)
            captured.stop()
        }
    }
}
