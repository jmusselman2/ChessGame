@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.server.api.CompletedGameReviewView
import com.jmussel.chessgame.server.api.ReviewPositionView
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.StoredGame
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.series.seriesService
import com.jmussel.chessgame.server.series.startSeries
import com.jmussel.chessgame.server.testModule
import com.jmussel.chessgame.server.user.Username
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * `GET /games/{gameId}/review` (`D100`, `D101`, `M22.10`): a finished game ply by ply, from the
 * recorded positions, for its participants only.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class ReviewTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private class FixedCoin(
        private val value: Boolean,
    ) : Random() {
        override fun nextBits(bitCount: Int): Int = 0

        override fun nextBoolean(): Boolean = value

        override fun nextInt(until: Int): Int = if (value) 0 else until - 1
    }

    private class Fixture(
        val database: Database,
    ) {
        val users = UserRepository(database)
        val games = GameRepository(database)
        val commands = GameCommandService(database, games, seriesService(database), users)
        lateinit var jordan: Uuid
        lateinit var alex: Uuid
        lateinit var gameId: Uuid

        fun start() {
            jordan = named("auth-1", "Jordan")
            alex = named("auth-2", "Alex")
            named("auth-3", "Sam")
            FriendshipRepository(database).add(jordan, alex)
            gameId = assertNotNull(seriesService(database, FixedCoin(jordan < alex)).startSeries(jordan, alex).currentGameId)
            check(game().whiteUserId == jordan)
        }

        private fun named(
            subject: String,
            username: String,
        ): Uuid {
            val user = users.resolveBySubject(subject)
            users.claimUsername(user.id, Username.of(username))
            return user.id
        }

        fun game(): StoredGame = assertNotNull(games.load(gameId))

        fun play(vararg moves: String) {
            moves.forEach { move ->
                val player = if (game().game.sideToMove == Side.WHITE) jordan else alex
                assertIs<CommandResult.Applied>(commands.makeMove(player, gameId, game().version, Move.of(move.take(2), move.drop(2))))
            }
        }
    }

    private fun withServer(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val fixture = Fixture(database).also { it.start() }
            testApplication {
                application { testModule(tokens.verifier(), database) }
                block(fixture)
            }
        }

    private suspend fun ApplicationTestBuilder.review(
        subject: String,
        gameId: Uuid,
    ): HttpResponse = client.get("/games/$gameId/review") { header("Authorization", "Bearer ${tokens.tokenFor(subject)}") }

    @Test
    fun aFinishedGameIsReviewedPlyByPlyFromItsRecordedPositions() {
        withServer { fixture ->
            fixture.play("f2f3", "e7e5", "g2g4", "d8h4")
            val stored = fixture.game()

            val response = review("auth-1", fixture.gameId)

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertEquals(
                setOf("gameId", "version", "plies", "finalPosition", "result", "terminationReason"),
                json.parseToJsonElement(body).jsonObject.keys,
                "nothing else leaves the server: $body",
            )
            val view = json.decodeFromString<CompletedGameReviewView>(body)
            assertEquals(fixture.gameId.toString(), view.gameId)
            assertEquals(stored.version, view.version)
            assertEquals(listOf("f3", "e5", "g4", "Qh4#"), view.plies.map { it.move.san })
            assertEquals(
                stored.game.history.map { ReviewPositionView.of(it.positionBefore) },
                view.plies.map { it.positionBefore },
                "each position is the one recorded before its move",
            )
            assertEquals(ReviewPositionView.of(stored.game.state), view.finalPosition)
            assertTrue(view.finalPosition.inCheck, "the final checkmate is a checked position")
            assertEquals("WHITE", view.finalPosition.sideToMove)
            assertEquals("BLACK_WINS", view.result)
            assertEquals("CHECKMATE", view.terminationReason)
            assertEquals(json.decodeFromString<CompletedGameReviewView>(review("auth-2", fixture.gameId).bodyAsText()), view)
        }
    }

    @Test
    fun aCheckBeforeTheEndIsMarkedOnItsPosition() {
        withServer { fixture ->
            fixture.play("e2e4", "d7d6", "f1b5")
            fixture.commands.resign(fixture.alex, fixture.gameId, fixture.game().version)

            val view = json.decodeFromString<CompletedGameReviewView>(review("auth-1", fixture.gameId).bodyAsText())

            assertEquals(
                "Bb5+",
                view.plies
                    .last()
                    .move.san,
            )
            assertFalse(
                view.plies
                    .last()
                    .positionBefore.inCheck,
            )
            assertTrue(view.finalPosition.inCheck, "Black is in check in the position the game ended in")
            assertEquals("RESIGNATION", view.terminationReason)
        }
    }

    @Test
    fun aZeroMoveFinishedGameHasOnlyItsFinalPosition() {
        withServer { fixture ->
            fixture.commands.resign(fixture.jordan, fixture.gameId, 0)

            val view = json.decodeFromString<CompletedGameReviewView>(review("auth-2", fixture.gameId).bodyAsText())

            assertEquals(emptyList(), view.plies)
            assertEquals(ReviewPositionView.of(fixture.game().game.state), view.finalPosition)
            assertEquals("BLACK_WINS", view.result)
        }
    }

    @Test
    fun aMissingGameAStrangerAndAGameInProgressAreRefused() {
        withServer { fixture ->
            fixture.play("e2e4")

            assertEquals(HttpStatusCode.NotFound, review("auth-1", Uuid.random()).status)
            assertEquals(HttpStatusCode.Forbidden, review("auth-3", fixture.gameId).status)
            assertEquals(HttpStatusCode.Conflict, review("auth-1", fixture.gameId).status, "an active game is never reviewed")
        }
    }

    @Test
    fun anInvalidRecordedMoveLeavesReviewUnavailableAndNeverShowsCoordinates() {
        withServer { fixture ->
            fixture.play("e2e4", "e7e5")
            fixture.commands.resign(fixture.alex, fixture.gameId, fixture.game().version)
            // Break the second move's recorded context: from its stored position it is not legal.
            val start =
                fixture
                    .game()
                    .game.history
                    .first()
                    .positionBefore
            transaction(fixture.database) {
                exec(
                    "update moves set position_before = " +
                        "(select position_before from moves where game_id = '${fixture.gameId}' and ply = 1) " +
                        "where game_id = '${fixture.gameId}' and ply = 2",
                )
            }
            assertFalse(ChessRules.isLegal(start, Move.of("e7", "e5")))

            val response = review("auth-1", fixture.gameId)

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertFalse(response.bodyAsText().contains("e7e5"), "no coordinate fallback")
        }
    }
}
