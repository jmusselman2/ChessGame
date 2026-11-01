package com.jmussel.chessgame.server.db

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Duration
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The limits on a request's connection (`D077`, `M17.12`): each is set on every pooled
 * connection, each ends what it is meant to end, and Flyway's connection has none.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class ConnectionTimeoutsTest {
    // One short limit per test, the others left long, so a test sees the limit it is about
    // and no other: with every limit short, the holder's idle limit ends its transaction, and
    // frees its lock, just as the waiter's lock limit falls due.
    private val shortStatement = ConnectionTimeouts(statement = Duration.ofMillis(300))
    private val shortLockWait = ConnectionTimeouts(lock = Duration.ofMillis(300))
    private val shortIdle = ConnectionTimeouts(idleInTransaction = Duration.ofMillis(300))

    private fun Connection.show(setting: String): String =
        createStatement().use { statement ->
            statement.executeQuery("show $setting").use { rows ->
                rows.next()
                rows.getString(1)
            }
        }

    private fun Connection.run(sql: String) = createStatement().use { it.execute(sql) }

    private fun Connection.backendPid(): Int =
        createStatement().use { statement ->
            statement.executeQuery("select pg_backend_pid()").use { rows ->
                check(rows.next())
                rows.getInt(1)
            }
        }

    private fun DataSource.anyUserId(): String =
        connection.use { connection ->
            connection
                .createStatement()
                .use { statement ->
                    statement.executeQuery("insert into users default values returning id").use { rows ->
                        rows.next()
                        rows.getString(1)
                    }
                }.also { connection.commit() }
        }

    @Test
    fun everyPooledConnectionCarriesTheDefaultLimits() =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            // Two at once, so this is not only the first connection the pool opened.
            dataSource.connection.use { first ->
                dataSource.connection.use { second ->
                    listOf(first, second).forEach { connection ->
                        assertEquals("30s", connection.show("statement_timeout"))
                        assertEquals("1min", connection.show("idle_in_transaction_session_timeout"))
                        assertEquals("10s", connection.show("lock_timeout"))
                    }
                }
            }
        }

    @Test
    fun initializationCommitsItsSettingsWithoutChangingAutocommit() {
        val base = DatabaseTestSupport.config ?: return
        val applicationName = "m23-initialization-${System.nanoTime()}"
        val separator = if ('?' in base.jdbcUrl) '&' else '?'
        val config =
            base.copy(
                jdbcUrl = "${base.jdbcUrl}$separator" + "ApplicationName=$applicationName",
                maximumPoolSize = 1,
                timeouts = ConnectionTimeouts(idleInTransaction = Duration.ofMillis(INITIALIZATION_IDLE_MILLIS)),
            )

        DriverManager.getConnection(base.jdbcUrl, base.username, base.password).use { observer ->
            val dataSource = config.dataSource()
            try {
                val initializedPid = waitForBackend(observer, applicationName)

                assertEquals("idle", backendState(observer, initializedPid))

                Thread.sleep(INITIALIZATION_WAIT_MILLIS)

                assertEquals("idle", backendState(observer, initializedPid))
                dataSource.connection.use { connection ->
                    assertFalse(connection.autoCommit)
                    assertEquals(initializedPid, connection.backendPid())
                    assertEquals("30s", connection.show("statement_timeout"))
                    assertEquals("500ms", connection.show("idle_in_transaction_session_timeout"))
                    assertEquals("10s", connection.show("lock_timeout"))
                    assertTrue(connection.createStatement().use { it.execute("select 1") })
                    connection.rollback()
                }
            } finally {
                (dataSource as AutoCloseable).close()
            }
        }
    }

    @Test
    fun initializationSqlEndsTheTransactionAfterApplyingEverySetting() {
        val sql = ConnectionTimeouts().initSql

        assertTrue(sql.contains("SET statement_timeout = 30000"))
        assertTrue(sql.contains("SET idle_in_transaction_session_timeout = 60000"))
        assertTrue(sql.contains("SET lock_timeout = 10000"))
        assertTrue(sql.endsWith("; COMMIT"))
    }

    @Test
    fun theMigrationConnectionHasNoLimits() {
        val config = DatabaseTestSupport.config ?: return

        config.migrationDataSource().use { dataSource ->
            dataSource.connection.use { connection ->
                assertEquals("0", connection.show("statement_timeout"))
                assertEquals("0", connection.show("idle_in_transaction_session_timeout"))
                assertEquals("0", connection.show("lock_timeout"))
            }
        }
    }

    @Test
    fun aStatementThatRunsTooLongIsCancelled() =
        DatabaseTestSupport.withMigratedDatabase(shortStatement) { dataSource ->
            dataSource.connection.use { connection ->
                val failure = assertFailsWith<SQLException> { connection.run("select pg_sleep(2)") }

                assertEquals(QUERY_CANCELED, failure.sqlState)
                connection.rollback()
            }
        }

    @Test
    fun waitingTooLongForALockFailsRatherThanWaiting() =
        DatabaseTestSupport.withMigratedDatabase(shortLockWait) { dataSource ->
            val userId = dataSource.anyUserId()

            dataSource.connection.use { holder ->
                holder.run("select id from users where id = '$userId' for update")

                dataSource.connection.use { waiter ->
                    val failure =
                        assertFailsWith<SQLException> {
                            waiter.run("select id from users where id = '$userId' for update")
                        }

                    assertEquals(LOCK_NOT_AVAILABLE, failure.sqlState)
                    waiter.rollback()
                }

                holder.rollback()
            }
        }

    @Test
    fun aTransactionLeftIdleIsEndedAndItsLockReleased() =
        DatabaseTestSupport.withMigratedDatabase(shortIdle) { dataSource ->
            val userId = dataSource.anyUserId()

            dataSource.connection.use { idle ->
                idle.run("select id from users where id = '$userId' for update")

                Thread.sleep(IDLE_WAIT_MILLIS)

                assertFailsWith<SQLException>("the server ended the session") { idle.run("select 1") }
            }

            // The lock went with it: another connection takes it at once.
            dataSource.connection.use { next ->
                next.run("select id from users where id = '$userId' for update")
                next.rollback()
            }
        }

    private fun waitForBackend(
        observer: Connection,
        applicationName: String,
    ): Int {
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (System.nanoTime() < deadline) {
            observer
                .prepareStatement(
                    "select pid from pg_stat_activity where application_name = ? and datname = current_database()",
                ).use { statement ->
                    statement.setString(1, applicationName)
                    statement.executeQuery().use { rows ->
                        if (rows.next()) return rows.getInt(1)
                    }
                }
            Thread.sleep(10)
        }

        return assertNotNull(null, "Hikari did not initialize its physical connection")
    }

    private fun backendState(
        observer: Connection,
        pid: Int,
    ): String? =
        observer.prepareStatement("select state from pg_stat_activity where pid = ?").use { statement ->
            statement.setInt(1, pid)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }

    private companion object {
        /** `query_canceled`: what a statement timeout raises. */
        const val QUERY_CANCELED = "57014"

        /** `lock_not_available`: what a lock timeout raises. */
        const val LOCK_NOT_AVAILABLE = "55P03"

        /** Comfortably past the 300 ms idle limit. */
        const val IDLE_WAIT_MILLIS = 1_000L

        const val INITIALIZATION_IDLE_MILLIS = 500L
        const val INITIALIZATION_WAIT_MILLIS = 1_200L
    }
}
