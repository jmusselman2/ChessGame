@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.server.api.CommandRejection
import com.jmussel.chessgame.server.api.DashboardEntry
import com.jmussel.chessgame.server.api.GameView
import com.jmussel.chessgame.server.api.RejectionReason
import com.jmussel.chessgame.server.api.SeriesSummary
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.testModule
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
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * Two authenticated people playing each other through the API, with nothing shared between
 * them but the server.
 *
 * Everything here goes over HTTP with two different tokens: sign in, become friends, open a
 * series, and alternate moves. Neither client is told anything the server did not decide.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class TwoClientGameTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    /** One player's client: a token and the last version of the game they saw. */
    private inner class Client(
        val builder: ApplicationTestBuilder,
        val subject: String,
    ) {
        suspend fun claimUsername(name: String) =
            builder.client.post("/username") {
                authorized()
                setBody(name)
            }

        suspend fun addFriend(name: String) =
            builder.client.post("/friends") {
                authorized()
                setBody(name)
            }

        suspend fun openSeries(friend: String): SeriesSummary =
            json.decodeFromString(
                builder.client
                    .post("/series") {
                        authorized()
                        setBody(friend)
                    }.bodyAsText(),
            )

        suspend fun dashboard(): List<DashboardEntry> =
            json.decodeFromString(
                builder.client
                    .get("/dashboard") { authorized() }
                    .bodyAsText(),
            )

        suspend fun readGame(gameId: String): GameView =
            json.decodeFromString(
                builder.client
                    .get("/games/$gameId") { authorized() }
                    .bodyAsText(),
            )

        suspend fun move(
            gameId: String,
            version: Long,
            from: String,
            to: String,
        ): HttpResponse =
            builder.client.post("/games/$gameId/moves") {
                authorized()
                contentType(ContentType.Application.Json)
                setBody("""{"expectedVersion":$version,"from":"$from","to":"$to"}""")
            }

        private fun io.ktor.client.request.HttpRequestBuilder.authorized() = header("Authorization", "Bearer ${tokens.tokenFor(subject)}")
    }

    private fun withTwoClients(block: suspend ApplicationTestBuilder.(Client, Client) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)

            testApplication {
                application { testModule(tokens.verifier(), database) }
                block(Client(this, "auth-jordan"), Client(this, "auth-alex"))
            }
        }

    /** Both clients sign in, become friends, and open the series; returns the game id. */
    private suspend fun ApplicationTestBuilder.startGame(
        jordan: Client,
        alex: Client,
    ): String {
        jordan.claimUsername("Jordan")
        alex.claimUsername("Alex")
        jordan.addFriend("Alex")

        val series = jordan.openSeries("Alex")

        return requireNotNull(series.currentGameId) { "opening a series starts its first game" }
    }

    @Test
    fun twoPlayersAlternateMovesThroughTheApi() {
        withTwoClients { jordan, alex ->
            val gameId = startGame(jordan, alex)

            val jordanView = jordan.readGame(gameId)
            val alexView = alex.readGame(gameId)

            assertNotEquals(jordanView.yourSide, alexView.yourSide, "they play opposite colours")
            assertTrue(jordanView.yourTurn != alexView.yourTurn, "exactly one of them is to move")

            // Whoever has White starts; the fixture does not care which of them it is.
            val (first, second) = if (jordanView.yourTurn) jordan to alex else alex to jordan
            val firstMoves = listOf("e2" to "e4", "g1" to "f3")
            val secondMoves = listOf("e7" to "e5", "b8" to "c6")

            var version = 0L
            firstMoves.zip(secondMoves).forEach { (whiteMove, blackMove) ->
                assertEquals(
                    HttpStatusCode.OK,
                    first.move(gameId, version, whiteMove.first, whiteMove.second).status,
                )
                version += 1

                assertEquals(
                    HttpStatusCode.OK,
                    second.move(gameId, version, blackMove.first, blackMove.second).status,
                )
                version += 1
            }

            val finalView = first.readGame(gameId)

            assertEquals(4, finalView.version)
            assertEquals(listOf("e2e4", "e7e5", "g1f3", "b8c6"), finalView.moves)
            assertTrue(finalView.yourTurn, "back to the player who started")
        }
    }

    @Test
    fun eachClientSeesTheOthersMoveOnTheirNextRead() {
        withTwoClients { jordan, alex ->
            val gameId = startGame(jordan, alex)
            val (first, second) = if (jordan.readGame(gameId).yourTurn) jordan to alex else alex to jordan

            val beforeTheMove = second.readGame(gameId)

            assertFalse(beforeTheMove.yourTurn)
            assertTrue(beforeTheMove.moves.isEmpty())

            first.move(gameId, 0, "d2", "d4")

            val afterTheMove = second.readGame(gameId)

            assertTrue(afterTheMove.yourTurn, "the opponent's move handed them the turn")
            assertEquals(listOf("d2d4"), afterTheMove.moves)
            assertEquals(1, afterTheMove.version)
        }
    }

    @Test
    fun aPlayerCannotMoveTwiceInARow() {
        withTwoClients { jordan, alex ->
            val gameId = startGame(jordan, alex)
            val (first, _) = if (jordan.readGame(gameId).yourTurn) jordan to alex else alex to jordan

            assertEquals(HttpStatusCode.OK, first.move(gameId, 0, "e2", "e4").status)

            val again = first.move(gameId, 1, "d2", "d4")

            assertEquals(HttpStatusCode.Conflict, again.status)
            assertEquals(
                RejectionReason.NOT_YOUR_TURN,
                json.decodeFromString<CommandRejection>(again.bodyAsText()).reason,
            )
            assertEquals(1, first.readGame(gameId).version, "nothing was written")
        }
    }

    @Test
    fun bothDashboardsFollowTheSameGame() {
        withTwoClients { jordan, alex ->
            val gameId = startGame(jordan, alex)
            val (first, second) = if (jordan.readGame(gameId).yourTurn) jordan to alex else alex to jordan

            first.move(gameId, 0, "e2", "e4")

            val waiting = first.dashboard().single()
            val toPlay = second.dashboard().single()

            assertEquals(gameId, waiting.gameId)
            assertEquals(gameId, toPlay.gameId)
            assertFalse(waiting.yourTurn)
            assertTrue(toPlay.yourTurn)
            assertEquals(1, waiting.version)
            assertEquals(waiting.version, toPlay.version, "one canonical version, two views")
        }
    }

    @Test
    fun aWholeGameIsPlayedToCheckmateByTwoClients() {
        withTwoClients { jordan, alex ->
            val gameId = startGame(jordan, alex)
            val (white, black) = if (jordan.readGame(gameId).yourTurn) jordan to alex else alex to jordan

            // Fool's mate: White obliges, Black punishes.
            assertEquals(HttpStatusCode.OK, white.move(gameId, 0, "f2", "f3").status)
            assertEquals(HttpStatusCode.OK, black.move(gameId, 1, "e7", "e5").status)
            assertEquals(HttpStatusCode.OK, white.move(gameId, 2, "g2", "g4").status)

            val mating = black.move(gameId, 3, "d8", "h4")

            assertEquals(HttpStatusCode.OK, mating.status)

            val forBlack = json.decodeFromString<GameView>(mating.bodyAsText())
            val forWhite = white.readGame(gameId)

            assertTrue(forBlack.isOver)
            assertEquals("CHECKMATE", forBlack.terminationReason)
            assertEquals(forBlack.result, forWhite.result, "both are told the same outcome")
            assertEquals(4, forWhite.version)
            assertFalse(forWhite.yourTurn, "a finished game is nobody's move")
        }
    }

    @Test
    fun neitherPlayerCanMoveOnceTheGameIsOver() {
        withTwoClients { jordan, alex ->
            val gameId = startGame(jordan, alex)
            val (white, black) = if (jordan.readGame(gameId).yourTurn) jordan to alex else alex to jordan

            white.move(gameId, 0, "f2", "f3")
            black.move(gameId, 1, "e7", "e5")
            white.move(gameId, 2, "g2", "g4")
            black.move(gameId, 3, "d8", "h4")

            listOf(white, black).forEach { player ->
                val response = player.move(gameId, 4, "b1", "c3")

                assertEquals(HttpStatusCode.Conflict, response.status)
                assertEquals(
                    RejectionReason.GAME_OVER,
                    json.decodeFromString<CommandRejection>(response.bodyAsText()).reason,
                )
            }
        }
    }

    @Test
    fun aThirdPersonCanNeitherReadNorPlay() {
        withTwoClients { jordan, alex ->
            val gameId = startGame(jordan, alex)
            val sam = Client(this, "auth-sam")
            sam.claimUsername("Sam")

            assertEquals(
                HttpStatusCode.Forbidden,
                builderResponse(sam, gameId).status,
            )
            assertEquals(HttpStatusCode.Forbidden, sam.move(gameId, 0, "e2", "e4").status)
        }
    }

    private suspend fun ApplicationTestBuilder.builderResponse(
        client: Client,
        gameId: String,
    ): HttpResponse =
        this.client.get("/games/$gameId") {
            header("Authorization", "Bearer ${tokens.tokenFor(client.subject)}")
        }
}
