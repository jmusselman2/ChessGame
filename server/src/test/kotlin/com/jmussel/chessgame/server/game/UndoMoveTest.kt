@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.server.api.CommandRejection
import com.jmussel.chessgame.server.api.GameView
import com.jmussel.chessgame.server.api.RejectionReason
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.StoredGame
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.series.seriesService
import com.jmussel.chessgame.server.testModule
import com.jmussel.chessgame.server.user.Username
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Taking a move back, with the product rule enforced by the server rather than the client.
 *
 * The rule (`D016`): your own latest move, only while the opponent has not answered, and
 * never once the game has ended.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class UndoMoveTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private class FixedCoin(
        private val value: Boolean,
    ) : Random() {
        override fun nextBits(bitCount: Int): Int = 0

        override fun nextBoolean(): Boolean = value
    }

    private class Fixture(
        val database: Database,
        val users: UserRepository,
        val games: GameRepository,
        val commands: GameCommandService,
    ) {
        lateinit var white: Uuid
        lateinit var black: Uuid
        lateinit var gameId: Uuid

        fun startGame() {
            val jordan = named("auth-1", "Jordan")
            val alex = named("auth-2", "Alex")
            FriendshipRepository(database).add(jordan, alex)

            val opened = seriesService(database, FixedCoin(jordan < alex)).openWithGame(jordan, alex)

            gameId = assertNotNull(opened.series.currentGameId)
            white = jordan
            black = alex
        }

        fun named(
            subject: String,
            username: String,
        ): Uuid {
            val user = users.resolveBySubject(subject)
            users.claimUsername(user.id, Username.of(username))
            return user.id
        }

        fun game(): StoredGame = assertNotNull(games.load(gameId))

        fun play(
            player: Uuid,
            from: String,
            to: String,
        ): StoredGame {
            val result = commands.makeMove(player, gameId, game().version, Move.of(from, to))
            assertTrue(result is CommandResult.Applied, "setup move $from$to failed: $result")
            return result.game
        }
    }

    private fun withGame(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val games = GameRepository(database)
            val fixture =
                Fixture(database, UserRepository(database), games, GameCommandService(database, games, seriesService(database)))
            fixture.startGame()
            block(fixture)
        }

    private fun withServer(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val games = GameRepository(database)
            val fixture =
                Fixture(database, UserRepository(database), games, GameCommandService(database, games, seriesService(database)))
            fixture.startGame()

            testApplication {
                application { testModule(tokens.verifier(), database) }
                block(fixture)
            }
        }

    private suspend fun ApplicationTestBuilder.undo(
        subject: String,
        gameId: Uuid,
        expectedVersion: Long,
    ) = client.post("/games/$gameId/undo") {
        header("Authorization", "Bearer ${tokens.tokenFor(subject)}")
        contentType(ContentType.Application.Json)
        setBody("""{"expectedVersion":$expectedVersion}""")
    }

    @Test
    fun aPlayerCanTakeBackTheirOwnUnansweredMove() {
        withGame { fixture ->
            fixture.play(fixture.white, "g1", "f3")

            val result = fixture.commands.undoMove(fixture.white, fixture.gameId, 1)

            assertTrue(result is CommandResult.Applied, "expected the undo to be accepted, got $result")

            val after = fixture.game()

            assertEquals(ChessGame.newGame(), after.game, "the position is exactly what it was")
            assertTrue(after.game.moves.isEmpty())
        }
    }

    @Test
    fun anUndoIsAnAcceptedMutationSoTheVersionMovesOn() {
        withGame { fixture ->
            fixture.play(fixture.white, "g1", "f3")

            fixture.commands.undoMove(fixture.white, fixture.gameId, 1)

            assertEquals(2, fixture.game().version, "the undo is itself a version (D021)")
        }
    }

    @Test
    fun theOpponentCannotTakeBackYourMove() {
        withGame { fixture ->
            fixture.play(fixture.white, "g1", "f3")

            val result = fixture.commands.undoMove(fixture.black, fixture.gameId, 1)

            assertTrue(result is CommandResult.NothingToUndo)
            assertEquals(listOf(Move.of("g1", "f3")), fixture.game().game.moves)
        }
    }

    @Test
    fun anAnsweredMoveCannotBeTakenBack() {
        withGame { fixture ->
            fixture.play(fixture.white, "g1", "f3")
            fixture.play(fixture.black, "g8", "f6")

            val result = fixture.commands.undoMove(fixture.white, fixture.gameId, 2)

            assertTrue(result is CommandResult.NothingToUndo, "Alex answered, so Nf3 is locked")
            assertEquals(
                2,
                fixture
                    .game()
                    .game.moves.size,
            )
        }
    }

    @Test
    fun theAnsweringPlayerCanTakeTheirOwnAnswerBack() {
        withGame { fixture ->
            fixture.play(fixture.white, "g1", "f3")
            fixture.play(fixture.black, "g8", "f6")

            val result = fixture.commands.undoMove(fixture.black, fixture.gameId, 2)

            assertTrue(result is CommandResult.Applied)
            assertEquals(listOf(Move.of("g1", "f3")), fixture.game().game.moves)
        }
    }

    @Test
    fun takingBackAnAnswerMakesThePreviousMoveUndoableAgain() {
        withGame { fixture ->
            fixture.play(fixture.white, "g1", "f3")
            fixture.play(fixture.black, "g8", "f6")
            fixture.commands.undoMove(fixture.black, fixture.gameId, 2)

            val result = fixture.commands.undoMove(fixture.white, fixture.gameId, 3)

            assertTrue(result is CommandResult.Applied, "Nf3 is the latest unanswered move again")
            assertTrue(
                fixture
                    .game()
                    .game.moves
                    .isEmpty(),
            )
        }
    }

    @Test
    fun thereIsNothingToTakeBackAtTheStart() {
        withGame { fixture ->
            listOf(fixture.white, fixture.black).forEach { player ->
                assertTrue(fixture.commands.undoMove(player, fixture.gameId, 0) is CommandResult.NothingToUndo)
            }
            assertEquals(0, fixture.game().version)
        }
    }

    @Test
    fun aGameEndingMoveCannotBeTakenBack() {
        withGame { fixture ->
            fixture.play(fixture.white, "f2", "f3")
            fixture.play(fixture.black, "e7", "e5")
            fixture.play(fixture.white, "g2", "g4")
            fixture.play(fixture.black, "d8", "h4")

            val finished = fixture.game()

            assertTrue(finished.isComplete)

            val result = fixture.commands.undoMove(fixture.black, fixture.gameId, finished.version)

            assertTrue(result is CommandResult.GameOver, "a mating move is final (D017)")
            assertEquals(finished.version, fixture.game().version)
            assertTrue(fixture.game().isComplete)
        }
    }

    @Test
    fun aStrangerCannotTakeAnythingBack() {
        withGame { fixture ->
            fixture.play(fixture.white, "g1", "f3")
            val stranger = fixture.named("auth-3", "Sam")

            assertEquals(
                CommandResult.NotAParticipant,
                fixture.commands.undoMove(stranger, fixture.gameId, 1),
            )
        }
    }

    @Test
    fun aStaleUndoIsRefused() {
        withGame { fixture ->
            fixture.play(fixture.white, "g1", "f3")

            val result = fixture.commands.undoMove(fixture.white, fixture.gameId, 0)

            assertTrue(result is CommandResult.StaleVersion)
            assertEquals(listOf(Move.of("g1", "f3")), fixture.game().game.moves)
        }
    }

    @Test
    fun theSamePlayerCannotTakeBackTwice() {
        withGame { fixture ->
            fixture.play(fixture.white, "g1", "f3")
            fixture.commands.undoMove(fixture.white, fixture.gameId, 1)

            val again = fixture.commands.undoMove(fixture.white, fixture.gameId, 2)

            assertTrue(again is CommandResult.NothingToUndo)
        }
    }

    @Test
    fun aTakenBackMoveCanBeReplacedWithAnother() {
        withGame { fixture ->
            fixture.play(fixture.white, "g1", "f3")
            fixture.commands.undoMove(fixture.white, fixture.gameId, 1)

            val replacement = fixture.play(fixture.white, "e2", "e4")

            assertEquals(listOf(Move.of("e2", "e4")), replacement.game.moves)
            assertEquals(3, fixture.game().version)
        }
    }

    @Test
    fun anAcceptedUndoIsAudited() {
        withGame { fixture ->
            fixture.play(fixture.white, "g1", "f3")
            fixture.commands.undoMove(fixture.white, fixture.gameId, 1)

            assertEquals(listOf("MoveMade", "MoveUndone"), fixture.games.auditTrail(fixture.gameId))
        }
    }

    @Test
    fun theGameViewOffersUndoToTheRightPlayerOnly() {
        withServer { fixture ->
            fixture.play(fixture.white, "g1", "f3")

            val forWhite =
                json.decodeFromString<GameView>(
                    client
                        .get("/games/${fixture.gameId}") {
                            header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                        }.bodyAsText(),
                )
            val forBlack =
                json.decodeFromString<GameView>(
                    client
                        .get("/games/${fixture.gameId}") {
                            header("Authorization", "Bearer ${tokens.tokenFor("auth-2")}")
                        }.bodyAsText(),
                )

            assertTrue(forWhite.canUndo, "White made the latest move")
            assertFalse(forBlack.canUndo)
        }
    }

    @Test
    fun theEndpointTakesTheMoveBack() {
        withServer { fixture ->
            fixture.play(fixture.white, "g1", "f3")

            val response = undo("auth-1", fixture.gameId, 1)

            assertEquals(HttpStatusCode.OK, response.status)

            val view = json.decodeFromString<GameView>(response.bodyAsText())

            assertEquals(2, view.version)
            assertTrue(view.moves.isEmpty())
            assertTrue(view.yourTurn, "it is White's move again")
            assertFalse(view.canUndo, "and there is nothing left to take back")
        }
    }

    @Test
    fun theEndpointRefusesAnUndoThatIsNotYours() {
        withServer { fixture ->
            fixture.play(fixture.white, "g1", "f3")

            val response = undo("auth-2", fixture.gameId, 1)

            assertEquals(HttpStatusCode.Conflict, response.status)
            assertEquals(
                RejectionReason.NOTHING_TO_UNDO,
                json.decodeFromString<CommandRejection>(response.bodyAsText()).reason,
            )
        }
    }

    @Test
    fun theEndpointNeedsAToken() {
        withServer { fixture ->
            val response =
                client.post("/games/${fixture.gameId}/undo") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"expectedVersion":1}""")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
}
