@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.realtime.RealtimeHub
import com.jmussel.chessgame.server.realtime.RealtimeMessage
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.close
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import ch.qos.logback.classic.Level as LogbackLevel

/**
 * A failure the server recovers from must still leave a trace (`M19.12`).
 *
 * Best-effort delivery (`D022`) is a reason not to *retry*, not a reason to fail silently.
 * A dropped socket, a stalled one, and a rejected token are all handled and all invisible
 * before this — and "the opponent never saw my move" is exactly the beta report that a
 * silent `catch` makes unanswerable.
 *
 * Every line these assert on is also checked for what it must **not** contain: `M16.5`'s
 * policy is that a log is read somewhere with weaker access rules than the database, so no
 * token and no credential material may reach it.
 */
class FailureLoggingTest {
    private val tokens = TestTokens()

    /** Everything logged while [block] runs, at [level] and above. */
    private fun captureLogs(
        level: Level = Level.INFO,
        block: (MutableList<ILoggingEvent>) -> Unit,
    ) {
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val captured = ListAppender<ILoggingEvent>().apply { start() }
        val restore = root.level

        root.level = LogbackLevel.toLevel(level.name)
        root.addAppender(captured)
        try {
            block(captured.list)
        } finally {
            root.detachAppender(captured)
            root.level = restore
            captured.stop()
        }
    }

    private fun List<ILoggingEvent>.text(): String = joinToString(separator = "\n") { it.formattedMessage }

    private fun List<ILoggingEvent>.at(level: Level): List<ILoggingEvent> = filter { it.level.toString() == level.name }

    // --- A forced connection failure -----------------------------------------------------

    @Test
    fun aConnectionThatFailsToTakeAnUpdateIsLogged() {
        captureLogs { logged ->
            val hub = RealtimeHub()
            val gone = Uuid.random()
            hub.subscribe(gone) { throw IOException("connection reset by peer") }

            runBlocking { hub.publish(listOf(gone), RealtimeMessage.gameUpdated(Uuid.random(), version = 3)) }

            val line = logged.at(Level.INFO).map { it.formattedMessage }.single { it.contains("Realtime send") }
            assertTrue(line.contains(gone.toString()), "the line names which client was dropped: $line")
            assertTrue(line.contains("IOException"), "and what went wrong: $line")
            assertEquals(0, hub.connectionCount(gone), "and the connection really was dropped")
        }
    }

    @Test
    fun aConnectionThatStallsPastItsDeadlineIsLoggedMoreLoudly() {
        captureLogs { logged ->
            // A socket that neither delivers nor fails is the `M12-01` pathology, so it is a
            // warning rather than an ordinary dropped client.
            val hub = RealtimeHub(sendTimeout = 200.milliseconds)
            val stalled = Uuid.random()
            hub.subscribe(stalled) { awaitCancellation() }

            runBlocking {
                withTimeout(10.seconds) { hub.publish(listOf(stalled), RealtimeMessage.gameUpdated(Uuid.random(), version = 1)) }
            }

            val line = logged.at(Level.WARN).map { it.formattedMessage }.single { it.contains("Realtime send") }
            assertTrue(line.contains("timed out"), "the line says what kind of failure it was: $line")
            assertTrue(line.contains(stalled.toString()), "and which client: $line")
        }
    }

    @Test
    fun aSuccessfulDeliveryLogsNothing() {
        captureLogs { logged ->
            val hub = RealtimeHub()
            val reachable = Uuid.random()
            hub.subscribe(reachable) { }

            runBlocking { hub.publish(listOf(reachable), RealtimeMessage.gameUpdated(Uuid.random(), version = 1)) }

            // A log worth reading is one that is quiet when nothing is wrong.
            assertFalse(logged.text().contains("Realtime send"), "a working socket is not news")
        }
    }

    @Test
    fun aDroppedSocketNamesNoCredential() {
        captureLogs { logged ->
            val hub = RealtimeHub()
            val gone = Uuid.random()
            // A failure message carrying something that looks like a credential must not be
            // relayed into the log wholesale.
            hub.subscribe(gone) { throw IOException("Bearer eyJhbGciOiJFUzI1NiJ9.secret.signature") }

            runBlocking { hub.publish(listOf(gone), RealtimeMessage.gameUpdated(Uuid.random(), version = 1)) }

            val text = logged.text()
            assertTrue(text.contains("IOException"), "the failure type is logged")
            assertFalse(text.contains("eyJhbGciOiJFUzI1NiJ9"), "but not whatever the message quoted: $text")
            assertFalse(text.contains("Bearer"), "and nothing that reads like a credential: $text")
        }
    }

    @Test
    fun theSocketLifecycleIsTraceableWithoutFillingTheLog() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)

            // At DEBUG, because opening and closing a socket is detail rather than news:
            // the abnormal end beside it is the one that reaches INFO.
            captureLogs(Level.DEBUG) { logged ->
                testApplication {
                    application { testModule(tokens.verifier(), database) }

                    val session =
                        createClient { install(WebSockets) }
                            .webSocketSession("/ws") { header("Authorization", "Bearer ${tokens.tokenFor("logging-socket")}") }
                    session.incoming.receive()
                    session.close()
                }

                val text = logged.text()
                assertTrue(text.contains("Realtime socket opened"), "opening is traceable: $text")
                assertTrue(text.contains("Realtime socket closed"), "and so is closing: $text")
            }
        }
    }

    @Test
    fun anOrdinarySocketLifecycleSaysNothingAtInfo() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)

            captureLogs { logged ->
                testApplication {
                    application { testModule(tokens.verifier(), database) }

                    val session =
                        createClient { install(WebSockets) }
                            .webSocketSession("/ws") { header("Authorization", "Bearer ${tokens.tokenFor("logging-socket-2")}") }
                    session.incoming.receive()
                    session.close()
                }

                // The whole socket lifecycle is DEBUG, including ending with an exception:
                // an ordinary client close reaches either path depending on which side wins
                // the race, so it carries no signal. What reaches INFO is a *send* that
                // failed — a specific player missing a specific update.
                assertFalse(logged.text().contains("Realtime socket"), "a normal connection is not worth an INFO line")
            }
        }
    }

    // --- A rejected token ---------------------------------------------------------------

    @Test
    fun aRejectedTokenIsLoggedWithoutTheToken() {
        val forged = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJmb3JnZWQifQ.not-a-signature"

        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)

            captureLogs { logged ->
                testApplication {
                    application { testModule(tokens.verifier(), database) }

                    val response = client.get("/me") { header("Authorization", "Bearer $forged") }
                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                }

                val text = logged.text()
                assertTrue(text.contains("Rejected a bearer token"), "a refused token is recorded: $text")
                assertTrue(text.contains("/me"), "with the route it was refused on: $text")

                // The JWT library's own messages can quote the malformed token back, which is
                // why the reason is deliberately not relayed (`M16.5`).
                assertFalse(text.contains(forged), "the token itself must never be logged")
                assertFalse(text.contains("eyJzdWIiOiJmb3JnZWQifQ"), "nor any part of it: $text")
            }
        }
    }

    @Test
    fun aValidTokenIsNotLoggedAsRejected() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)

            captureLogs { logged ->
                testApplication {
                    application { testModule(tokens.verifier(), database) }
                    assertEquals(HttpStatusCode.OK, me("logging-valid").status)
                }

                assertFalse(logged.text().contains("Rejected a bearer token"), "a good token is not a refusal")
            }
        }
    }

    // --- An unhandled command error ------------------------------------------------------

    @Test
    fun anErrorThatEscapesARouteReachesTheLogAtError() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)

            captureLogs { logged ->
                testApplication {
                    application {
                        testModule(tokens.verifier(), database)
                        routing {
                            get("/failing-command") {
                                error("the database fell over mid-command")
                            }
                        }
                    }

                    client.get("/failing-command")
                }

                // Ktor's own handler covers this one, which is why nothing new was added for
                // it: the assertion exists so that stays true.
                val text = logged.at(Level.ERROR).map { it.formattedMessage }.joinToString("\n")
                assertTrue(text.isNotEmpty(), "an unhandled command error is logged at ERROR")
            }
        }
    }

    private suspend fun ApplicationTestBuilder.me(subject: String) =
        client.get("/me") { header("Authorization", "Bearer ${tokens.tokenFor(subject)}") }
}
