@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.jmussel.chessgame.server.api.CurrentUser
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.realtime.RealtimeMessage
import com.jmussel.chessgame.server.testModule
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
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
    private val json = Json { ignoreUnknownKeys = true }

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

    // --- A refused write does not stop the request (D080) ------------------------------

    /**
     * The startup path: the app restores its session and asks `GET /me` who it is. A
     * `last_seen_at` write that fails must not turn that into a server error, because the
     * token was good and the user was found — the request is valid and only the telemetry
     * was lost.
     */
    @Test
    fun aRefusedLastSeenWriteDoesNotStopAValidRequest() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val users = UserRepository(database)
            val existing = users.resolveBySubject("auth-1")
            val at = Instant.parse("2026-08-26T10:00:00Z")

            refuseLastSeenWrites(dataSource)
            captureLogs { logged ->
                testApplication {
                    application { testModule(tokens.verifier(), database, LastSeenTracker(users, clock = { at })) }

                    val token = tokens.tokenFor("auth-1")
                    val response = client.get("/me") { header("Authorization", "Bearer $token") }
                    val body = response.bodyAsText()

                    assertEquals(HttpStatusCode.OK, response.status, body)
                    // The route ran as the token's user: it read the principal to answer.
                    assertEquals(existing.id.toString(), json.decodeFromString<CurrentUser>(body).userId)
                    ENGAGEMENT_FIELDS.forEach { leak ->
                        assertFalse(body.contains(leak, ignoreCase = true), "/me leaked $leak")
                    }

                    val line = logged.map { it.formattedMessage }.single { it.contains("Could not record activity") }
                    assertTrue(line.contains(existing.id.toString()), "the line names the user: $line")
                    assertTrue(line.contains("/me"), "and the request: $line")
                    val everything = logged.joinToString("\n") { "${it.formattedMessage} ${it.throwableProxy?.message}" }
                    assertFalse(everything.contains(token), "no token reaches the log")
                    assertFalse(everything.contains("Bearer"), "nor the header it came in")
                }
            }

            assertNull(users.find(existing.id)?.lastSeenAt, "the refused write really was refused")
            assertNotNull(users.find(existing.id)?.lastLoginAt, "and the session start was still recorded")
        }
    }

    /**
     * The refused write hands its window back, so the very next request — well inside five
     * minutes — writes, and the one that lands is what starts the quiet.
     */
    @Test
    fun theRequestAfterARefusedWriteRetriesIt() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val users = UserRepository(database)
            val user = users.resolveBySubject("auth-1")
            var now = Instant.parse("2026-08-26T10:00:00Z")
            val tracker = LastSeenTracker(users, throttle = Duration.ofMinutes(5), clock = { now })

            testApplication {
                application { testModule(tokens.verifier(), database, tracker) }
                val authorization = "Bearer ${tokens.tokenFor("auth-1")}"

                refuseLastSeenWrites(dataSource)
                assertEquals(HttpStatusCode.OK, client.get("/me") { header("Authorization", authorization) }.status)
                assertNull(users.find(user.id)?.lastSeenAt)

                allowLastSeenWrites(dataSource)
                now = now.plusSeconds(1)
                assertEquals(HttpStatusCode.OK, client.get("/friends") { header("Authorization", authorization) }.status)
                val retried = now
                assertEquals(retried, users.find(user.id)?.lastSeenAt, "the next request wrote")

                now = now.plusSeconds(30)
                client.get("/friends") { header("Authorization", authorization) }
                assertEquals(retried, users.find(user.id)?.lastSeenAt, "and the throttle is back in force")

                now = retried.plus(Duration.ofMinutes(5))
                client.get("/friends") { header("Authorization", authorization) }
                assertEquals(now, users.find(user.id)?.lastSeenAt, "until its five minutes are up")
            }
        }
    }

    /** Being lenient about telemetry is not being lenient about the token. */
    @Test
    fun aRefusedLastSeenWriteLetsNoBadTokenIn() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val users = UserRepository(database)

            refuseLastSeenWrites(dataSource)
            testApplication {
                application { testModule(tokens.verifier(), database, LastSeenTracker(users)) }

                val missing = client.get("/me")
                assertEquals(HttpStatusCode.Unauthorized, missing.status)
                assertEquals("Missing bearer token", missing.bodyAsText())

                listOf(
                    tokens.tokenFromAnotherKey("auth-1"),
                    tokens.tokenFor("auth-1", expiresAt = Instant.now().minusSeconds(60)),
                ).forEach { bad ->
                    val refused = client.get("/me") { header("Authorization", "Bearer $bad") }
                    assertEquals(HttpStatusCode.Unauthorized, refused.status)
                    assertEquals("Invalid bearer token", refused.bodyAsText())
                }
            }
        }
    }

    /**
     * The leniency covers `last_seen_at` and nothing else. A user who cannot be resolved is
     * not a caller, and that failure still stops the request before any route runs.
     */
    @Test
    fun aFailureToResolveTheUserIsStillAFailure() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val users = UserRepository(database)

            execute(
                dataSource,
                """
                create function refuse_new_users() returns trigger language plpgsql as
                ${'$'}body${'$'}
                begin
                    raise exception 'user creation refused';
                end
                ${'$'}body${'$'};
                create trigger refuse_new_users
                    before insert on users
                    for each row execute function refuse_new_users()
                """.trimIndent(),
            )

            testApplication {
                application { testModule(tokens.verifier(), database, LastSeenTracker(users)) }

                val response = client.get("/me") { header("Authorization", "Bearer ${tokens.tokenFor("auth-new")}") }

                assertEquals(HttpStatusCode.InternalServerError, response.status, "no route answered as a signed-in user")
            }
        }
    }

    /** `/ws` sits behind the same provider, so the realtime socket gets the same leniency. */
    @Test
    fun aRefusedLastSeenWriteDoesNotStopTheRealtimeSocket() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val users = UserRepository(database)
            users.resolveBySubject("auth-1")

            refuseLastSeenWrites(dataSource)
            testApplication {
                application { testModule(tokens.verifier(), database, LastSeenTracker(users)) }

                val session =
                    createClient { install(WebSockets) }.webSocketSession("/ws") {
                        header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                    }
                val hello = withTimeout(5_000) { (session.incoming.receive() as Frame.Text).readText() }

                assertEquals(RealtimeMessage.CONNECTED, json.decodeFromString<RealtimeMessage>(hello).type)
                session.close()
            }
        }
    }

    /** Everything logged while [block] runs. */
    private fun captureLogs(block: (List<ILoggingEvent>) -> Unit) {
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val captured = ListAppender<ILoggingEvent>().apply { start() }

        root.addAppender(captured)
        try {
            block(captured.list)
        } finally {
            root.detachAppender(captured)
            captured.stop()
        }
    }

    /**
     * Makes any `last_seen_at` update fail, the way a database that is refusing writes would.
     *
     * Only that column: `last_login_at` is written on the same request and must stay free to
     * succeed, or a test could not tell the two apart.
     */
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
                before update of last_seen_at on users
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

    private companion object {
        val ENGAGEMENT_FIELDS = listOf("lastLogin", "last_login", "lastAction", "last_action", "lastSeen", "last_seen")
    }
}
