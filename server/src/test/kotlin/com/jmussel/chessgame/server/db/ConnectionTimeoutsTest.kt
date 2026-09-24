package com.jmussel.chessgame.server.db

import java.sql.Connection
import java.sql.SQLException
import java.time.Duration
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    private fun DataSource.anyUserId(): String =
        connection.use { connection ->
            connection.run("insert into users (auth_subject) values ('timeouts-test')")
            connection.commit()
            connection.createStatement().use { statement ->
                statement.executeQuery("select id from users where auth_subject = 'timeouts-test'").use { rows ->
                    rows.next()
                    rows.getString(1)
                }
            }
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

    private companion object {
        /** `query_canceled`: what a statement timeout raises. */
        const val QUERY_CANCELED = "57014"

        /** `lock_not_available`: what a lock timeout raises. */
        const val LOCK_NOT_AVAILABLE = "55P03"

        /** Comfortably past the 300 ms idle limit. */
        const val IDLE_WAIT_MILLIS = 1_000L
    }
}
