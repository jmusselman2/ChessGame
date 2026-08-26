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
 * The same command arriving twice.
 *
 * A phone on a bad connection retries, a player taps twice, a request succeeds and its
 * reply is lost. None of that may play a move twice, and the client must be able to tell
 * from the refusal alone that its own command already landed — the version it acted on is
 * what makes a command unique, so a duplicate is a stale one (`D021`).
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class DuplicateCommandTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private fun withGame(block: suspend ApplicationTestBuilder.(String) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)

            testApplication {
                application { testModule(tokens.verifier(), database) }
                block(startGame())
            }
        }

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
    ): GameView =
        json.decodeFromString(
            client
                .get("/games/$gameId") { authorizedAs(subject) }
                .bodyAsText(),
        )

    /** Whoever is to move first, then the other one. */
    private suspend fun ApplicationTestBuilder.order(gameId: String): Pair<String, String> =
        if (readGame(JORDAN, gameId).yourTurn) JORDAN to ALEX else ALEX to JORDAN

    private suspend fun ApplicationTestBuilder.move(
        subject: String,
        gameId: String,
        version: Long,
        from: String,
        to: String,
    ): HttpResponse =
        client.post("/games/$gameId/moves") {
            authorizedAs(subject)
            contentType(ContentType.Application.Json)
            setBody("""{"expectedVersion":$version,"from":"$from","to":"$to"}""")
        }

    private suspend fun ApplicationTestBuilder.undo(
        subject: String,
        gameId: String,
        version: Long,
    ): HttpResponse =
        client.post("/games/$gameId/undo") {
            authorizedAs(subject)
            contentType(ContentType.Application.Json)
            setBody("""{"expectedVersion":$version}""")
        }

    private fun rejection(body: String): CommandRejection = json.decodeFromString(body)

    @Test
    fun theSameMoveSentTwiceIsPlayedOnce() {
        withGame { gameId ->
            val (mover, _) = order(gameId)

            assertEquals(HttpStatusCode.OK, move(mover, gameId, 0, "e2", "e4").status)

            val again = move(mover, gameId, 0, "e2", "e4")

            assertEquals(HttpStatusCode.Conflict, again.status)
            assertEquals(RejectionReason.STALE_VERSION, rejection(again.bodyAsText()).reason)

            val game = readGame(mover, gameId)

            assertEquals(listOf("e2e4"), game.moves, "the pawn did not move twice")
            assertEquals(1, game.version)
        }
    }

    @Test
    fun aRetryCanSeeItsOwnMoveInTheRefusal() {
        withGame { gameId ->
            val (mover, _) = order(gameId)
            move(mover, gameId, 0, "e2", "e4")

            // The reply to the first request was lost, so the client sends it again.
            val again = move(mover, gameId, 0, "e2", "e4")
            val current = assertNotNull(rejection(again.bodyAsText()).game)

            // The refusal carries the canonical state, so the client can see that the move
            // it was retrying is the one already played, without a second request.
            assertEquals(listOf("e2e4"), current.moves)
            assertEquals(1, current.version)
        }
    }

    @Test
    fun aRetryAtTheNewVersionPlaysTheMoveAgainAsANewMove() {
        withGame { gameId ->
            val (mover, waiter) = order(gameId)
            move(mover, gameId, 0, "g1", "f3")
            move(waiter, gameId, 1, "g8", "f6")

            // Sending the same squares at the version the game is *now* at is not a
            // duplicate — it is a legal knight move back, and it is meant to be allowed.
            assertEquals(HttpStatusCode.OK, move(mover, gameId, 2, "f3", "g1").status)
            assertEquals(listOf("g1f3", "g8f6", "f3g1"), readGame(mover, gameId).moves)
        }
    }

    @Test
    fun theSameUndoSentTwiceTakesBackOneMove() {
        withGame { gameId ->
            val (mover, _) = order(gameId)
            move(mover, gameId, 0, "e2", "e4")

            assertEquals(HttpStatusCode.OK, undo(mover, gameId, 1).status)

            val again = undo(mover, gameId, 1)

            assertEquals(HttpStatusCode.Conflict, again.status)
            assertEquals(RejectionReason.STALE_VERSION, rejection(again.bodyAsText()).reason)

            val game = readGame(mover, gameId)

            assertTrue(game.moves.isEmpty(), "one move was played and one was taken back")
            assertEquals(2, game.version, "the undo counted once")
        }
    }

    @Test
    fun anUndoRetriedAtTheNewVersionFindsNothingToTakeBack() {
        withGame { gameId ->
            val (mover, _) = order(gameId)
            move(mover, gameId, 0, "e2", "e4")
            undo(mover, gameId, 1)

            // A client that refreshed and tried again: the version is right, but the move
            // it wanted to take back is already gone.
            val again = undo(mover, gameId, 2)

            assertEquals(HttpStatusCode.Conflict, again.status)
            assertEquals(RejectionReason.NOTHING_TO_UNDO, rejection(again.bodyAsText()).reason)
            assertEquals(2, readGame(mover, gameId).version, "nothing was written")
        }
    }

    @Test
    fun aDuplicateResignationEndsTheGameOnce() {
        withGame { gameId ->
            assertEquals(HttpStatusCode.OK, resign(JORDAN, gameId, 0).status)
            val afterTheFirst = readGame(JORDAN, gameId)

            val again = resign(JORDAN, gameId, 0)

            assertEquals(HttpStatusCode.Conflict, again.status)
            assertEquals(RejectionReason.STALE_VERSION, rejection(again.bodyAsText()).reason)

            val now = readGame(JORDAN, gameId)

            assertEquals(afterTheFirst.version, now.version)
            assertEquals("RESIGNATION", now.terminationReason)
        }
    }

    @Test
    fun aResignationRetriedAtTheNewVersionIsRefusedAsFinished() {
        withGame { gameId ->
            resign(JORDAN, gameId, 0)

            val again = resign(JORDAN, gameId, 1)

            assertEquals(HttpStatusCode.Conflict, again.status)
            assertEquals(RejectionReason.GAME_OVER, rejection(again.bodyAsText()).reason)
        }
    }

    @Test
    fun aDuplicateMoveDoesNotDisturbTheOpponent() {
        withGame { gameId ->
            val (mover, waiter) = order(gameId)
            move(mover, gameId, 0, "d2", "d4")
            move(mover, gameId, 0, "d2", "d4")

            val opponentsView = readGame(waiter, gameId)

            assertTrue(opponentsView.yourTurn, "it is still their move, once")
            assertEquals(listOf("d2d4"), opponentsView.moves)
            assertEquals(1, opponentsView.version)
        }
    }

    @Test
    fun tenIdenticalRequestsLeaveOneMove() {
        withGame { gameId ->
            val (mover, _) = order(gameId)

            val statuses = (1..10).map { move(mover, gameId, 0, "e2", "e4").status }

            assertEquals(1, statuses.count { it == HttpStatusCode.OK }, "exactly one was applied")
            assertEquals(9, statuses.count { it == HttpStatusCode.Conflict })

            val game = readGame(mover, gameId)

            assertEquals(listOf("e2e4"), game.moves)
            assertEquals(1, game.version)
        }
    }

    @Test
    fun aDuplicateDrawClaimEndsTheGameOnce() {
        withGame { gameId ->
            val (first, second) = order(gameId)

            // Knights out and back, twice: the starting position appears three times.
            val rally = listOf("g1" to "f3", "g8" to "f6", "f3" to "g1", "f6" to "g8")
            var version = 0L
            repeat(2) {
                rally.forEachIndexed { index, (from, to) ->
                    val player = if (index % 2 == 0) first else second

                    assertEquals(HttpStatusCode.OK, move(player, gameId, version, from, to).status)
                    version += 1
                }
            }

            assertEquals(HttpStatusCode.OK, claimDraw(first, gameId, version).status)

            val again = claimDraw(first, gameId, version)

            assertEquals(HttpStatusCode.Conflict, again.status)
            assertEquals(RejectionReason.STALE_VERSION, rejection(again.bodyAsText()).reason)

            val game = readGame(first, gameId)

            assertEquals("THREEFOLD_REPETITION_CLAIM", game.terminationReason)
            assertEquals(version + 1, game.version, "the claim counted once")
        }
    }

    @Test
    fun aDuplicateMoveThatEndsTheGameCreatesOneRematch() {
        withGame { gameId ->
            val (first, second) = order(gameId)
            move(first, gameId, 0, "f2", "f3")
            move(second, gameId, 1, "e7", "e5")
            move(first, gameId, 2, "g2", "g4")

            assertEquals(HttpStatusCode.OK, move(second, gameId, 3, "d8", "h4").status)
            assertEquals(HttpStatusCode.Conflict, move(second, gameId, 3, "d8", "h4").status)

            val dashboard =
                json.decodeFromString<List<com.jmussel.chessgame.server.api.DashboardEntry>>(
                    client.get("/dashboard") { authorizedAs(JORDAN) }.bodyAsText(),
                )
            val entry = dashboard.single()

            assertTrue(entry.gameId != gameId, "the series moved on to the rematch")
            assertEquals(0, entry.version, "and only one rematch was created")
        }
    }

    private suspend fun ApplicationTestBuilder.claimDraw(
        subject: String,
        gameId: String,
        version: Long,
    ): HttpResponse =
        client.post("/games/$gameId/draw-claims") {
            authorizedAs(subject)
            contentType(ContentType.Application.Json)
            setBody("""{"expectedVersion":$version,"claim":"THREEFOLD_REPETITION"}""")
        }

    private suspend fun ApplicationTestBuilder.resign(
        subject: String,
        gameId: String,
        version: Long,
    ): HttpResponse =
        client.post("/games/$gameId/resignation") {
            authorizedAs(subject)
            contentType(ContentType.Application.Json)
            setBody("""{"expectedVersion":$version}""")
        }

    private companion object {
        const val JORDAN = "auth-jordan"
        const val ALEX = "auth-alex"
    }
}
