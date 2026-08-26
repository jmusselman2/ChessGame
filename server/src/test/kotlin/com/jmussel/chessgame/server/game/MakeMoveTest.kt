@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.server.api.CommandRejection
import com.jmussel.chessgame.server.api.GameView
import com.jmussel.chessgame.server.api.RejectionReason
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
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
 * Playing a move through the server, which is the only thing that decides whether it
 * happened.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class MakeMoveTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    /** A coin that always lands the same way, so colours are known in a test. */
    private class FixedCoin(
        private val value: Boolean,
    ) : Random() {
        override fun nextBits(bitCount: Int): Int = 0

        override fun nextBoolean(): Boolean = value
    }

    private class Fixture(
        val database: Database,
        val users: UserRepository,
        val friendships: FriendshipRepository,
        val series: GameSeriesRepository,
        val games: GameRepository,
        val commands: GameCommandService,
    ) {
        lateinit var white: Uuid
        lateinit var black: Uuid
        lateinit var gameId: Uuid

        /** Jordan (`auth-1`) plays White, Alex (`auth-2`) plays Black, in a fresh game. */
        fun startGame() {
            val jordan = named("auth-1", "Jordan")
            val alex = named("auth-2", "Alex")
            friendships.add(jordan, alex)

            val jordanIsLower = jordan < alex
            val opened = seriesService(database, FixedCoin(jordanIsLower)).openWithGame(jordan, alex)

            gameId = assertNotNull(opened.series.currentGameId)
            white = jordan
            black = alex

            val stored = assertNotNull(games.load(gameId))
            assertEquals(jordan, stored.whiteUserId, "the fixture means Jordan to play White")
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

        fun applied(result: CommandResult): StoredGame {
            assertTrue(result is CommandResult.Applied, "expected the move to be applied, got $result")
            return result.game
        }
    }

    private fun withGame(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val games = GameRepository(database)
            val fixture =
                Fixture(
                    database,
                    UserRepository(database),
                    FriendshipRepository(database),
                    GameSeriesRepository(database),
                    games,
                    GameCommandService(database, games, seriesService(database)),
                )
            fixture.startGame()
            block(fixture)
        }

    private fun withServer(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val games = GameRepository(database)
            val fixture =
                Fixture(
                    database,
                    UserRepository(database),
                    FriendshipRepository(database),
                    GameSeriesRepository(database),
                    games,
                    GameCommandService(database, games, seriesService(database)),
                )
            fixture.startGame()

            testApplication {
                application { testModule(tokens.verifier(), database) }
                block(fixture)
            }
        }

    private suspend fun ApplicationTestBuilder.move(
        subject: String,
        gameId: Uuid,
        expectedVersion: Long,
        from: String,
        to: String,
        promotion: String? = null,
    ) = client.post("/games/$gameId/moves") {
        header("Authorization", "Bearer ${tokens.tokenFor(subject)}")
        contentType(ContentType.Application.Json)
        setBody(
            """{"expectedVersion":$expectedVersion,"from":"$from","to":"$to"""" +
                (promotion?.let { ""","promotion":"$it"""" } ?: "") +
                "}",
        )
    }

    @Test
    fun aLegalMoveIsAppliedAndTheVersionMovesOn() {
        withGame { fixture ->
            val before = fixture.game()

            val applied =
                fixture.applied(
                    fixture.commands.makeMove(fixture.white, fixture.gameId, before.version, Move.of("e2", "e4")),
                )

            assertEquals(before.version + 1, applied.version)
            assertEquals(listOf(Move.of("e2", "e4")), applied.game.moves)
            assertEquals(Side.BLACK, applied.game.sideToMove)
        }
    }

    @Test
    fun theCanonicalStateIsWhatTheEngineProduced() {
        withGame { fixture ->
            val applied =
                fixture.applied(
                    fixture.commands.makeMove(fixture.white, fixture.gameId, 0, Move.of("e2", "e4")),
                )
            val expected =
                com.jmussel.chessgame.core.chess.ChessRules
                    .applyMove(ChessGame.newGame(), Move.of("e2", "e4"))

            assertEquals(expected, applied.game, "the server ran game-core, it did not take the client's word")
            assertEquals(expected, fixture.game().game, "and it persisted that")
        }
    }

    @Test
    fun theTwoPlayersAlternate() {
        withGame { fixture ->
            fixture.commands.makeMove(fixture.white, fixture.gameId, 0, Move.of("e2", "e4"))
            val second =
                fixture.applied(
                    fixture.commands.makeMove(fixture.black, fixture.gameId, 1, Move.of("e7", "e5")),
                )

            assertEquals(2, second.version)
            assertEquals(listOf(Move.of("e2", "e4"), Move.of("e7", "e5")), second.game.moves)
        }
    }

    @Test
    fun aStrangerCannotMove() {
        withGame { fixture ->
            val stranger = fixture.named("auth-3", "Sam")

            val result = fixture.commands.makeMove(stranger, fixture.gameId, 0, Move.of("e2", "e4"))

            assertEquals(CommandResult.NotAParticipant, result)
            assertEquals(0, fixture.game().version, "nothing was written")
        }
    }

    @Test
    fun aPlayerCannotMoveOutOfTurn() {
        withGame { fixture ->
            val result = fixture.commands.makeMove(fixture.black, fixture.gameId, 0, Move.of("e7", "e5"))

            assertTrue(result is CommandResult.NotYourTurn)
            assertEquals(0, fixture.game().version)
        }
    }

    @Test
    fun aPlayerCannotMoveTheOpponentsPieces() {
        withGame { fixture ->
            val result = fixture.commands.makeMove(fixture.white, fixture.gameId, 0, Move.of("e7", "e5"))

            assertTrue(result is CommandResult.IllegalMove)
            assertEquals(0, fixture.game().version)
        }
    }

    @Test
    fun anIllegalMoveIsRefused() {
        withGame { fixture ->
            val result = fixture.commands.makeMove(fixture.white, fixture.gameId, 0, Move.of("e2", "e5"))

            assertTrue(result is CommandResult.IllegalMove)
            assertEquals(ChessGame.newGame(), fixture.game().game, "the position is untouched")
        }
    }

    @Test
    fun aCommandOnAnOldVersionIsRefused() {
        withGame { fixture ->
            fixture.commands.makeMove(fixture.white, fixture.gameId, 0, Move.of("e2", "e4"))

            val stale = fixture.commands.makeMove(fixture.black, fixture.gameId, 0, Move.of("e7", "e5"))

            assertTrue(stale is CommandResult.StaleVersion)
            assertEquals(1, fixture.game().version, "the losing command wrote nothing")
        }
    }

    @Test
    fun aCommandOnAFutureVersionIsRefused() {
        withGame { fixture ->
            val result = fixture.commands.makeMove(fixture.white, fixture.gameId, 7, Move.of("e2", "e4"))

            assertTrue(result is CommandResult.StaleVersion)
            assertEquals(0, fixture.game().version)
        }
    }

    @Test
    fun anUnknownGameIsNotFound() {
        withGame { fixture ->
            assertEquals(
                CommandResult.NoSuchGame,
                fixture.commands.makeMove(fixture.white, Uuid.random(), 0, Move.of("e2", "e4")),
            )
        }
    }

    @Test
    fun aFinishedGameAcceptsNoMoreMoves() {
        withGame { fixture ->
            // Fool's mate, through the server.
            listOf(
                Triple(fixture.white, 0L, Move.of("f2", "f3")),
                Triple(fixture.black, 1L, Move.of("e7", "e5")),
                Triple(fixture.white, 2L, Move.of("g2", "g4")),
                Triple(fixture.black, 3L, Move.of("d8", "h4")),
            ).forEach { (player, version, move) ->
                fixture.applied(fixture.commands.makeMove(player, fixture.gameId, version, move))
            }

            val finished = fixture.game()

            assertTrue(finished.isComplete)
            assertEquals(4, finished.version)

            val afterwards =
                fixture.commands.makeMove(fixture.white, fixture.gameId, 4, Move.of("e1", "f2"))

            assertTrue(afterwards is CommandResult.GameOver)
            assertEquals(4, fixture.game().version)
        }
    }

    @Test
    fun everyAcceptedMoveIsAudited() {
        withGame { fixture ->
            fixture.commands.makeMove(fixture.white, fixture.gameId, 0, Move.of("e2", "e4"))
            fixture.commands.makeMove(fixture.black, fixture.gameId, 1, Move.of("e7", "e5"))
            fixture.commands.makeMove(fixture.white, fixture.gameId, 0, Move.of("d2", "d4"))

            assertEquals(
                listOf("MoveMade", "MoveMade"),
                fixture.games.auditTrail(fixture.gameId),
                "only the accepted commands are recorded",
            )
        }
    }

    @Test
    fun theEndpointPlaysAMove() {
        withServer { fixture ->
            val response = move("auth-1", fixture.gameId, 0, "e2", "e4")

            assertEquals(HttpStatusCode.OK, response.status)

            val view = json.decodeFromString<GameView>(response.bodyAsText())

            assertEquals(1, view.version)
            assertEquals("WHITE", view.yourSide)
            assertEquals("BLACK", view.sideToMove)
            assertFalse(view.yourTurn)
            assertEquals(listOf("e2e4"), view.moves)
        }
    }

    @Test
    fun theEndpointRefusesMovingOutOfTurnAndSaysWhereTheGameIs() {
        withServer { fixture ->
            val response = move("auth-2", fixture.gameId, 0, "e7", "e5")

            assertEquals(HttpStatusCode.Conflict, response.status)

            val rejection = json.decodeFromString<CommandRejection>(response.bodyAsText())

            assertEquals(RejectionReason.NOT_YOUR_TURN, rejection.reason)
            assertEquals(0, rejection.game?.version, "the reply carries the canonical state to correct from")
            assertEquals("WHITE", rejection.game?.sideToMove)
        }
    }

    @Test
    fun theEndpointRefusesAnIllegalMove() {
        withServer { fixture ->
            val response = move("auth-1", fixture.gameId, 0, "e2", "e5")

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        }
    }

    @Test
    fun theEndpointRefusesAStranger() {
        withServer { fixture ->
            fixture.named("auth-3", "Sam")

            val response = move("auth-3", fixture.gameId, 0, "e2", "e4")

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    @Test
    fun theEndpointRejectsRubbish() {
        withServer { fixture ->
            assertEquals(HttpStatusCode.BadRequest, move("auth-1", fixture.gameId, 0, "e2", "e9").status)
            assertEquals(HttpStatusCode.BadRequest, move("auth-1", fixture.gameId, 0, "e2", "e2").status)
            assertEquals(
                HttpStatusCode.BadRequest,
                move("auth-1", fixture.gameId, 0, "a7", "a8", promotion = "KING").status,
            )
        }
    }

    @Test
    fun theEndpointNeedsAToken() {
        withServer { fixture ->
            val response =
                client.post("/games/${fixture.gameId}/moves") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"expectedVersion":0,"from":"e2","to":"e4"}""")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun aPlayerCanReadTheCanonicalGame() {
        withServer { fixture ->
            move("auth-1", fixture.gameId, 0, "e2", "e4")

            val response =
                client.get("/games/${fixture.gameId}") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-2")}")
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val view = json.decodeFromString<GameView>(response.bodyAsText())

            assertEquals(1, view.version)
            assertEquals("BLACK", view.yourSide)
            assertTrue(view.yourTurn)
            assertEquals(8, view.board.size)
            assertEquals("rnbqkbnr", view.board.first())
        }
    }

    @Test
    fun aStrangerCannotReadTheGame() {
        withServer { fixture ->
            fixture.named("auth-3", "Sam")

            val response =
                client.get("/games/${fixture.gameId}") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-3")}")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    @Test
    fun aPromotionIsPlayedWithItsChoice() {
        withGame { fixture ->
            // Walk a white pawn to the eighth rank, alternating properly.
            val moves =
                listOf(
                    fixture.white to Move.of("a2", "a4"),
                    fixture.black to Move.of("b7", "b5"),
                    fixture.white to Move.of("a4", "b5"),
                    fixture.black to Move.of("b8", "c6"),
                    fixture.white to Move.of("b5", "b6"),
                    fixture.black to Move.of("c6", "d4"),
                    fixture.white to Move.of("b6", "b7"),
                    fixture.black to Move.of("d4", "c6"),
                    fixture.white to Move.of("b7", "a8", com.jmussel.chessgame.core.chess.PieceType.KNIGHT),
                )

            moves.forEachIndexed { index, (player, move) ->
                fixture.applied(fixture.commands.makeMove(player, fixture.gameId, index.toLong(), move))
            }

            val finished = fixture.game()

            assertEquals(9, finished.version)
            assertEquals(
                com.jmussel.chessgame.core.chess.PieceType.KNIGHT,
                finished.game.lastMove
                    ?.promotion,
            )
        }
    }
}
