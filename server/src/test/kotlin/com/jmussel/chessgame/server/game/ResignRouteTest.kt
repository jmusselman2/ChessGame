@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.server.api.CommandRejection
import com.jmussel.chessgame.server.api.GameView
import com.jmussel.chessgame.server.api.RejectionReason
import com.jmussel.chessgame.server.api.SeriesSummary
import com.jmussel.chessgame.server.auth.TestTokens
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * Resigning over HTTP.
 *
 * The command itself is covered by `ResignationTest`; this is about the endpoint — that a
 * player can reach it with their own token, that the reply is the finished game, and that
 * a refusal comes back in the same machine-readable envelope as any other (`M10.3`).
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class ResignRouteTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private fun withTwoClients(block: suspend ApplicationTestBuilder.() -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)

            testApplication {
                application { testModule(tokens.verifier(), database) }
                block()
            }
        }

    private fun HttpRequestBuilder.authorizedAs(subject: String) = header("Authorization", "Bearer ${tokens.tokenFor(subject)}")

    /** Both players sign in, become friends, and open the series; returns the game id. */
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

    private suspend fun ApplicationTestBuilder.resign(
        subject: String,
        gameId: String,
        expectedVersion: Long,
    ): HttpResponse =
        client.post("/games/$gameId/resignation") {
            authorizedAs(subject)
            contentType(ContentType.Application.Json)
            setBody("""{"expectedVersion":$expectedVersion}""")
        }

    private suspend fun ApplicationTestBuilder.readGame(
        subject: String,
        gameId: String,
    ): GameView =
        json.decodeFromString(
            client
                .get("/games/$gameId") { authorizedAs(subject) }
                .bodyAsText(),
        )

    @Test
    fun aPlayerCanResignTheirGame() {
        withTwoClients {
            val gameId = startGame()

            val response = resign(JORDAN, gameId, 0)

            assertEquals(HttpStatusCode.OK, response.status)

            val view = json.decodeFromString<GameView>(response.bodyAsText())

            assertEquals("RESIGNATION", view.terminationReason)
            assertTrue(view.isOver)
            assertEquals(1, view.version)
        }
    }

    @Test
    fun theOpponentSeesTheResignationOnTheirNextRead() {
        withTwoClients {
            val gameId = startGame()

            resign(JORDAN, gameId, 0)

            val opponentsView = readGame(ALEX, gameId)

            assertEquals("RESIGNATION", opponentsView.terminationReason)
            assertEquals(
                if (opponentsView.yourSide == "WHITE") "WHITE_WINS" else "BLACK_WINS",
                opponentsView.result,
                "the player who did not resign won",
            )
        }
    }

    @Test
    fun resigningAFinishedGameIsRefusedWithTheCanonicalState() {
        withTwoClients {
            val gameId = startGame()
            resign(JORDAN, gameId, 0)

            val again = resign(ALEX, gameId, 1)

            assertEquals(HttpStatusCode.Conflict, again.status)

            val rejection = json.decodeFromString<CommandRejection>(again.bodyAsText())

            assertEquals(RejectionReason.GAME_OVER, rejection.reason)
            assertEquals("RESIGNATION", assertNotNull(rejection.game).terminationReason)
        }
    }

    @Test
    fun aStrangerCannotResignSomeoneElsesGame() {
        withTwoClients {
            val gameId = startGame()

            val response = resign("auth-stranger", gameId, 0)

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertTrue(readGame(JORDAN, gameId).result == null, "nothing was written")
        }
    }

    @Test
    fun theSeriesHasANewGameToPlayAfterwards() {
        withTwoClients {
            val gameId = startGame()

            resign(JORDAN, gameId, 0)

            val dashboard =
                json.decodeFromString<List<com.jmussel.chessgame.server.api.DashboardEntry>>(
                    client.get("/dashboard") { authorizedAs(JORDAN) }.bodyAsText(),
                )
            val entry = dashboard.single()

            assertTrue(entry.gameId != gameId, "the dashboard already shows the rematch (`D015`)")
            assertEquals(0, entry.version)
        }
    }

    private companion object {
        const val JORDAN = "auth-jordan"
        const val ALEX = "auth-alex"
    }
}
