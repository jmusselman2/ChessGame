@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.testModule
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.server.testing.testApplication
import java.time.Duration
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * `lastSeenAt` follows meaningful activity, and only writes occasionally.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class LastSeenTest {
    private val tokens = TestTokens()

    private fun withUsers(block: (UserRepository) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            block(UserRepository(Databases.connect(dataSource)))
        }

    @Test
    fun aNewUserHasNeverBeenSeen() {
        withUsers { users ->
            assertNull(users.resolveBySubject("auth-1").lastSeenAt)
        }
    }

    @Test
    fun activityIsRecorded() {
        withUsers { users ->
            val user = users.resolveBySubject("auth-1")
            val at = Instant.parse("2026-08-26T10:00:00Z")
            val tracker = LastSeenTracker(users, clock = { at })

            assertTrue(tracker.record(user.id))
            assertEquals(at, users.find(user.id)?.lastSeenAt)
        }
    }

    @Test
    fun repeatedActivityDoesNotKeepWriting() {
        withUsers { users ->
            val user = users.resolveBySubject("auth-1")
            var now = Instant.parse("2026-08-26T10:00:00Z")
            val tracker = LastSeenTracker(users, throttle = Duration.ofMinutes(5), clock = { now })

            assertTrue(tracker.record(user.id))

            now = now.plusSeconds(30)
            assertFalse(tracker.record(user.id), "a burst of requests is not a heartbeat")

            now = now.plusSeconds(60)
            assertFalse(tracker.record(user.id))

            assertEquals(
                Instant.parse("2026-08-26T10:00:00Z"),
                users.find(user.id)?.lastSeenAt,
                "the stored value is the first write",
            )
        }
    }

    @Test
    fun activityAfterTheThrottleIsRecordedAgain() {
        withUsers { users ->
            val user = users.resolveBySubject("auth-1")
            var now = Instant.parse("2026-08-26T10:00:00Z")
            val tracker = LastSeenTracker(users, throttle = Duration.ofMinutes(5), clock = { now })

            tracker.record(user.id)
            now = now.plus(Duration.ofMinutes(6))

            assertTrue(tracker.record(user.id))
            assertEquals(now, users.find(user.id)?.lastSeenAt)
        }
    }

    @Test
    fun usersAreThrottledSeparately() {
        withUsers { users ->
            val first = users.resolveBySubject("auth-1")
            val second = users.resolveBySubject("auth-2")
            val at = Instant.parse("2026-08-26T10:00:00Z")
            val tracker = LastSeenTracker(users, clock = { at })

            assertTrue(tracker.record(first.id))
            assertTrue(tracker.record(second.id), "another user's activity is their own")
            assertNotNull(users.find(second.id)?.lastSeenAt)
        }
    }

    /**
     * A window belongs to a write that landed. A failed one hands it straight back, and the
     * retry that succeeds is what starts the next five minutes of quiet.
     */
    @Test
    fun aWindowIsSpentByTheWriteThatSucceedsAndNotTheOneThatFailed() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val users = UserRepository(Databases.connect(dataSource))
            val user = users.resolveBySubject("auth-1")
            var now = Instant.parse("2026-08-26T10:00:00Z")
            val tracker = LastSeenTracker(users, throttle = Duration.ofMinutes(5), clock = { now })

            refuseLastSeenWrites(dataSource)
            assertFails { tracker.record(user.id) }
            assertNull(users.find(user.id)?.lastSeenAt)

            allowLastSeenWrites(dataSource)
            now = now.plusSeconds(1)
            assertTrue(tracker.record(user.id), "the failed write left the window unspent")
            val recorded = now

            now = now.plusSeconds(30)
            assertFalse(tracker.record(user.id), "the write that landed does spend it")
            assertEquals(recorded, users.find(user.id)?.lastSeenAt)
        }
    }

    /** A user whose write failed is not throttling anyone else. */
    @Test
    fun aFailedWriteForOneUserLeavesAnotherUsersWindowAlone() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val users = UserRepository(Databases.connect(dataSource))
            val failing = users.resolveBySubject("auth-1")
            val other = users.resolveBySubject("auth-2")
            val at = Instant.parse("2026-08-26T10:00:00Z")
            val tracker = LastSeenTracker(users, clock = { at })

            refuseLastSeenWrites(dataSource)
            assertFails { tracker.record(failing.id) }
            allowLastSeenWrites(dataSource)

            assertTrue(tracker.record(other.id))
            assertEquals(at, users.find(other.id)?.lastSeenAt)
            assertTrue(tracker.record(failing.id), "the failed user may write as soon as it can")
            assertEquals(at, users.find(failing.id)?.lastSeenAt)
        }
    }

    /** Makes any `last_seen_at` update fail, the way a database that is refusing writes would. */
    private fun refuseLastSeenWrites(dataSource: DataSource) =
        execute(
            dataSource,
            """
            create function refuse_last_seen() returns trigger language plpgsql as
            ${'$'}body${'$'}
            begin
                raise exception 'last_seen_at write refused';
            end
            ${'$'}body${'$'};
            create trigger refuse_last_seen
                before update on users
                for each row execute function refuse_last_seen()
            """.trimIndent(),
        )

    private fun allowLastSeenWrites(dataSource: DataSource) = execute(dataSource, "drop trigger refuse_last_seen on users")

    private fun execute(
        dataSource: DataSource,
        sql: String,
    ) = dataSource.connection.use { connection ->
        connection.createStatement().use { it.execute(sql) }
        connection.commit()
    }

    @Test
    fun theDefaultThrottleIsFiveMinutes() {
        assertEquals(Duration.ofMinutes(5), LastSeenTracker.DEFAULT_THROTTLE)
    }

    @Test
    fun anAuthenticatedRequestCountsAsActivity() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val users = UserRepository(database)
            val at = Instant.parse("2026-08-26T10:00:00Z")

            testApplication {
                application {
                    testModule(tokens.verifier(), database, LastSeenTracker(users, clock = { at }))
                }

                client.get("/me") { header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}") }
            }

            assertEquals(at, users.resolveBySubject("auth-1").lastSeenAt)
        }
    }

    @Test
    fun anUnauthenticatedRequestIsNotActivity() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val users = UserRepository(database)
            val existing = users.resolveBySubject("auth-1")

            testApplication {
                application {
                    testModule(tokens.verifier(), database, LastSeenTracker(users))
                }

                client.get("/health")
                client.get("/me")
                client.get("/me") { header("Authorization", "Bearer ${tokens.tokenFromAnotherKey("auth-1")}") }
            }

            assertNull(users.find(existing.id)?.lastSeenAt)
        }
    }
}
