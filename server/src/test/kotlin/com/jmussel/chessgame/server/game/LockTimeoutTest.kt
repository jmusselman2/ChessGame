package com.jmussel.chessgame.server.game

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import com.jmussel.chessgame.server.api.GameView
import com.jmussel.chessgame.server.api.SeriesSummary
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.ConnectionTimeouts
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.testModule
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * A command that runs out of time waiting for its game (`D077`, `M17.12`).
 *
 * Another connection holds the game's row, as a request stuck inside its transaction would.
 * The command gives up at the lock limit instead of waiting for ever, fails as an ordinary
 * server error, and changes nothing, so the player's retry is safe (`D021`).
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class LockTimeoutTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private val shortLockWait = ConnectionTimeouts(lock = Duration.ofMillis(500))

    private fun HttpRequestBuilder.authorizedAs(subject: String) = header("Authorization", "Bearer ${tokens.tokenFor(subject)}")

    private suspend fun ApplicationTestBuilder.startGame(): String {
        client.post("/username") {
            authorizedAs(JORDAN)
            setBody("Jordan")
        }
        client.post("/username") {
            authorizedAs(ALEX)
            setBody("Alex")
        }
        client.post("/friends") {
            authorizedAs(JORDAN)
            setBody("Alex")
        }

        val series =
            json.decodeFromString<SeriesSummary>(
                client
                    .post("/series") {
                        authorizedAs(JORDAN)
                        setBody("Alex")
                    }.bodyAsText(),
            )

        return assertNotNull(series.currentGameId)
    }

    private suspend fun ApplicationTestBuilder.readGame(
        subject: String,
        gameId: String,
    ): GameView = json.decodeFromString(client.get("/games/$gameId") { authorizedAs(subject) }.bodyAsText())

    private suspend fun ApplicationTestBuilder.move(
        subject: String,
        gameId: String,
    ): HttpResponse =
        client.post("/games/$gameId/moves") {
            authorizedAs(subject)
            contentType(ContentType.Application.Json)
            setBody("""{"expectedVersion":0,"from":"e2","to":"e4"}""")
        }

    @Test
    fun aMoveThatCannotGetItsGameFailsAndChangesNothing() =
        DatabaseTestSupport.withMigratedDatabase(shortLockWait) { dataSource ->
            testApplication {
                application { testModule(tokens.verifier(), Databases.connect(dataSource)) }
                val gameId = startGame()
                val mover = if (readGame(JORDAN, gameId).yourTurn) JORDAN else ALEX

                val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
                val logged = ListAppender<ILoggingEvent>().apply { start() }
                root.addAppender(logged)

                try {
                    dataSource.connection.use { holder ->
                        holder.createStatement().use { it.execute("select id from games where id = '$gameId' for update") }

                        val (response, took) = measureTimedValue { move(mover, gameId) }

                        assertEquals(HttpStatusCode.InternalServerError, response.status)
                        assertTrue(took.inWholeSeconds < DEFAULT_LOCK_SECONDS, "it gave up at this test's limit, not the default: $took")

                        holder.rollback()
                    }
                } finally {
                    root.detachAppender(logged)
                    logged.stop()
                }

                assertTrue(
                    logged.list.any { it.level == Level.ERROR && it.causedByLockTimeout() },
                    "the failure is logged, with PostgreSQL's reason",
                )

                val after = readGame(mover, gameId)
                assertEquals(0, after.version, "the timed-out move changed nothing")
                assertEquals(emptyList(), after.moves)

                // The same command, sent again once the row is free, is played.
                assertEquals(HttpStatusCode.OK, move(mover, gameId).status)
                assertEquals(listOf("e2e4"), readGame(mover, gameId).moves)
            }
        }

    /** Whether PostgreSQL's lock timeout is somewhere in the logged failure's causes. */
    private fun ILoggingEvent.causedByLockTimeout(): Boolean =
        generateSequence((throwableProxy as? ThrowableProxy)?.throwable) { it.cause }
            .any { (it as? SQLException)?.sqlState == LOCK_NOT_AVAILABLE }

    private companion object {
        const val JORDAN = "auth-jordan"
        const val ALEX = "auth-alex"

        /** `lock_not_available`: what a lock timeout raises. */
        const val LOCK_NOT_AVAILABLE = "55P03"

        /** `ConnectionTimeouts`' default lock limit, which this test's must beat. */
        const val DEFAULT_LOCK_SECONDS = 10
    }
}
