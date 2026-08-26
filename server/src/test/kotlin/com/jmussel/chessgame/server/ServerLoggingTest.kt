@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.jmussel.chessgame.server.api.GameView
import com.jmussel.chessgame.server.api.SeriesSummary
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * What the server writes down, and what it must never write down.
 *
 * A log is read by whoever is debugging the beta, which is a weaker place than the
 * database: a token or a key in it is a token or a key given away. These tests capture
 * everything logged during real requests and assert on all of it, rather than on the one
 * line a change happened to add.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class ServerLoggingTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    /** Everything logged while [block] runs. */
    private fun captureLogs(block: suspend ApplicationTestBuilder.(MutableList<ILoggingEvent>) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
            val captured = ListAppender<ILoggingEvent>().apply { start() }

            root.addAppender(captured)
            try {
                testApplication {
                    application { testModule(tokens.verifier(), database) }
                    block(captured.list)
                }
            } finally {
                root.detachAppender(captured)
                captured.stop()
            }
        }

    private fun HttpRequestBuilder.authorizedAs(subject: String) = header("Authorization", "Bearer ${tokens.tokenFor(subject)}")

    private fun List<ILoggingEvent>.text(): String = joinToString(separator = "\n") { it.formattedMessage }

    /** Signs both players in, befriends them, and returns the game they are given. */
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

    /** Whichever player the server says is to move; the colours are a coin toss (`D014`). */
    private suspend fun ApplicationTestBuilder.whoIsToMove(gameId: String): String {
        val jordans =
            json.decodeFromString<GameView>(
                client
                    .get("/games/$gameId") { authorizedAs(JORDAN) }
                    .bodyAsText(),
            )

        return if (jordans.yourTurn) JORDAN else ALEX
    }

    /** The other one. */
    private suspend fun ApplicationTestBuilder.whoIsWaiting(gameId: String) = if (whoIsToMove(gameId) == JORDAN) ALEX else JORDAN

    @Test
    fun aRequestIsLoggedWithItsMethodPathAndStatus() {
        captureLogs { logs ->
            val gameId = startGame()

            client.get("/games/$gameId") { authorizedAs(JORDAN) }

            assertTrue(
                logs.text().contains("GET /games/$gameId -> 200"),
                "expected the request in the log, got:\n${logs.text()}",
            )
        }
    }

    @Test
    fun theBearerTokenIsNeverLogged() {
        captureLogs { logs ->
            val gameId = startGame()
            val token = tokens.tokenFor(JORDAN)

            client.get("/games/$gameId") { authorizedAs(JORDAN) }
            client.get("/dashboard") { authorizedAs(JORDAN) }

            val text = logs.text()

            assertFalse(text.contains(token), "the access token reached the log")
            assertFalse(text.contains("Bearer"), "an Authorization header reached the log")
            assertFalse(text.lowercase().contains("authorization"), "a header name reached the log")
        }
    }

    @Test
    fun aRefusedCommandIsLoggedWithEnoughToExplainIt() {
        captureLogs { logs ->
            val gameId = startGame()

            // Whoever is not to move tries anyway, at a version that is real.
            val waiter = whoIsWaiting(gameId)

            client.post("/games/$gameId/moves") {
                authorizedAs(waiter)
                contentType(ContentType.Application.Json)
                setBody("""{"expectedVersion":0,"from":"e7","to":"e5"}""")
            }

            val text = logs.text()

            assertTrue(text.contains("game=$gameId"), "the game is named:\n$text")
            assertTrue(text.contains("expectedVersion=0"), "the version is recorded:\n$text")
            assertTrue(text.contains("outcome=NotYourTurn"), "the decision is recorded:\n$text")
        }
    }

    @Test
    fun theBoardIsNotLogged() {
        captureLogs { logs ->
            val gameId = startGame()
            val mover = whoIsToMove(gameId)

            client.post("/games/$gameId/moves") {
                authorizedAs(mover)
                contentType(ContentType.Application.Json)
                setBody("""{"expectedVersion":0,"from":"e2","to":"e4"}""")
            }

            val text = logs.text()

            // A log full of positions is a copy of the game state somewhere with weaker
            // access rules than the database.
            assertFalse(text.contains("rnbqkbnr"), "a board reached the log:\n$text")
            assertFalse(text.contains("pppppppp"), "a board reached the log:\n$text")
        }
    }

    @Test
    fun theHealthCheckIsNotLogged() {
        captureLogs { logs ->
            client.get("/health")

            assertFalse(logs.text().contains("/health"), "polling would bury everything else")
        }
    }

    @Test
    fun aLogLineAboutACommandNamesTheUserAndTheGame() {
        val line =
            commandLogLine(
                action = "POST /games/x/moves",
                userId = kotlin.uuid.Uuid.parse("11111111-1111-1111-1111-111111111111"),
                gameId = kotlin.uuid.Uuid.parse("22222222-2222-2222-2222-222222222222"),
                expectedVersion = 7,
                outcome = "StaleVersion",
            )

        assertEquals(
            "POST /games/x/moves user=11111111-1111-1111-1111-111111111111 " +
                "game=22222222-2222-2222-2222-222222222222 expectedVersion=7 outcome=StaleVersion",
            line,
        )
    }

    private companion object {
        const val JORDAN = "auth-jordan"
        const val ALEX = "auth-alex"
    }
}
