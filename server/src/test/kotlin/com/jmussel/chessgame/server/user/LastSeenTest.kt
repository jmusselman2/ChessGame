@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.module
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.server.testing.testApplication
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun theDefaultThrottleIsFiveMinutes() {
        assertEquals(Duration.ofMinutes(5), LastSeenTracker.DEFAULT_THROTTLE)
    }

    @Test
    fun anAuthenticatedRequestCountsAsActivity() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val users = UserRepository(Databases.connect(dataSource))
            val at = Instant.parse("2026-08-26T10:00:00Z")

            testApplication {
                application {
                    module(tokens.verifier(), users, LastSeenTracker(users, clock = { at }))
                }

                client.get("/me") { header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}") }
            }

            assertEquals(at, users.resolveBySubject("auth-1").lastSeenAt)
        }
    }

    @Test
    fun anUnauthenticatedRequestIsNotActivity() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val users = UserRepository(Databases.connect(dataSource))
            val existing = users.resolveBySubject("auth-1")

            testApplication {
                application { module(tokens.verifier(), users, LastSeenTracker(users)) }

                client.get("/health")
                client.get("/me")
                client.get("/me") { header("Authorization", "Bearer ${tokens.tokenFromAnotherKey("auth-1")}") }
            }

            assertNull(users.find(existing.id)?.lastSeenAt)
        }
    }
}
