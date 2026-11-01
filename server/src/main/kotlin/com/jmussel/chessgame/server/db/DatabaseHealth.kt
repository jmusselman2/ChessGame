package com.jmussel.chessgame.server.db

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.SQLException
import kotlin.time.Duration.Companion.nanoseconds

/** Three fixed, read-only checks of the database path the application actually uses. */
class DatabaseHealth private constructor(
    private val execute: (String) -> Boolean,
    private val nanoTime: () -> Long,
) {
    constructor(database: Database) :
        this(
            execute = { sql ->
                transaction(database) {
                    exec(sql) { rows ->
                        check(rows.next()) { "Database health query returned no row" }
                        rows.getBoolean(1)
                    } ?: false
                }
            },
            nanoTime = System::nanoTime,
        )

    internal constructor(execute: (String) -> Boolean) : this(execute, System::nanoTime)

    /** Runs each required statement in order, stopping at the first unhealthy result. */
    fun check(): DatabaseHealthResult {
        CHECKS.forEach { check ->
            val started = nanoTime()
            val value =
                try {
                    execute(check.sql)
                } catch (failure: Exception) {
                    logUnavailable(check.name, started, failureCategory(failure))
                    return DatabaseHealthResult.Unavailable
                }

            if (check.mustBeTrue && !value) {
                logUnavailable(check.name, started, FALSE_RESULT)
                return DatabaseHealthResult.Unavailable
            }

            LOG.debug(
                "Database health check={} result=ok durationMs={}",
                check.name,
                elapsedMillis(started),
            )
        }

        return DatabaseHealthResult.Healthy
    }

    private fun logUnavailable(
        check: String,
        started: Long,
        category: String,
    ) {
        LOG.warn(
            "Database health check={} result=unavailable durationMs={} category={}",
            check,
            elapsedMillis(started),
            category,
        )
    }

    private fun elapsedMillis(started: Long): Long = (nanoTime() - started).nanoseconds.inWholeMilliseconds

    private companion object {
        const val FALSE_RESULT = "required_result_false"

        val LOG = LoggerFactory.getLogger(DatabaseHealth::class.java)

        val CHECKS =
            listOf(
                Check(
                    name = "successful_migration",
                    sql =
                        """
                        SELECT EXISTS (
                            SELECT 1
                            FROM public.flyway_schema_history
                            WHERE success = true
                        );
                        """.trimIndent(),
                    mustBeTrue = true,
                ),
                Check(
                    name = "games_table",
                    sql =
                        """
                        SELECT EXISTS (
                            SELECT 1
                            FROM public.games
                        );
                        """.trimIndent(),
                ),
                Check(
                    name = "game_series_table",
                    sql =
                        """
                        SELECT EXISTS (
                            SELECT 1
                            FROM public.game_series
                        );
                        """.trimIndent(),
                ),
            )

        fun failureCategory(failure: Exception): String {
            val sqlFailure =
                generateSequence<Throwable>(failure) { it.cause }
                    .filterIsInstance<SQLException>()
                    .firstOrNull()
                    ?: return "unexpected"
            val state = sqlFailure.sqlState.orEmpty()

            return when {
                state.startsWith("08") -> "connection"
                state == "42501" -> "permission"
                state == "42P01" -> "missing_table"
                state == "57014" || state == "55P03" -> "timeout"
                else -> "sql"
            }
        }
    }

    private data class Check(
        val name: String,
        val sql: String,
        val mustBeTrue: Boolean = false,
    )
}

sealed interface DatabaseHealthResult {
    data object Healthy : DatabaseHealthResult

    data object Unavailable : DatabaseHealthResult
}
