@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.server.api.CapturedPieceView
import com.jmussel.chessgame.server.api.GameView
import com.jmussel.chessgame.server.api.MoveView
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.StoredGame
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.series.seriesService
import com.jmussel.chessgame.server.series.startSeries
import com.jmussel.chessgame.server.user.Username
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The captured pieces and material balance an online game view projects (`D104`, `M23.1`):
 * each move's captured piece from its recorded position, and White's material minus Black's
 * from the current one, the same for both players, through promotion, Undo and Redo.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class CapturedPieceProjectionTest {
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

        private fun mover(): Uuid = if (game().game.sideToMove == Side.WHITE) jordan else alex

        fun play(vararg moves: String) {
            moves.forEach { move ->
                val promotion = move.getOrNull(4)?.let(PieceType::fromLetter)
                val parsed = Move.of(move.take(2), move.substring(2, 4), promotion)
                assertIs<CommandResult.Applied>(commands.makeMove(mover(), gameId, game().version, parsed), move)
            }
        }

        fun view(viewer: Uuid): GameView = GameView.of(game(), viewer, assertNotNull(users.find(game().opponentOf(viewer))))

        fun views(): List<GameView> = listOf(view(jordan), view(alex))
    }

    private fun withGame(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource -> block(Fixture(Databases.connect(dataSource)).also { it.start() }) }

    private fun captures(view: GameView): List<CapturedPieceView?> = view.moves.map { it.captured }

    private fun piece(
        side: String,
        type: String,
    ) = CapturedPieceView(side = side, type = type)

    /** `1. a4 b5 2. axb5 a6 3. bxa6 Bb7 4. axb7 Nc6 5. bxa8=Q`: White's a-pawn takes three pieces and promotes. */
    private val toPromotion = listOf("a2a4", "b7b5", "a4b5", "a7a6", "b5a6", "c8b7", "a6b7", "b8c6", "b7a8Q")

    @Test
    fun bothPlayersSeeTheSameCapturesAndTheSameBalance() {
        withGame { fixture ->
            fixture.play("e2e4", "d7d5", "e4d5", "d8d5")

            val (forWhite, forBlack) = fixture.views()

            assertEquals(listOf(null, null, piece("BLACK", "PAWN"), piece("WHITE", "PAWN")), captures(forWhite))
            assertEquals(captures(forWhite), captures(forBlack))
            assertEquals(forWhite.lastMove, forBlack.lastMove)
            assertEquals(piece("WHITE", "PAWN"), forWhite.lastMove?.captured)
            assertEquals(0, forWhite.whiteMaterialBalanceQuarterPoints)
            assertEquals(0, forBlack.whiteMaterialBalanceQuarterPoints, "equal material is zero, not missing")
        }
    }

    @Test
    fun theBalanceIsWhitesMaterialMinusBlacksForBothPlayers() {
        withGame { fixture ->
            fixture.play("e2e4", "d7d5", "e4d5")

            fixture.views().forEach { view -> assertEquals(4, view.whiteMaterialBalanceQuarterPoints, view.yourSide) }
        }
    }

    @Test
    fun aPromotedQueenIsCapturedAsAQueenAndTheBalanceCountsTheLivePieces() {
        withGame { fixture ->
            fixture.play(*toPromotion.dropLast(1).toTypedArray())
            // White has everything; Black is two pawns and a bishop down: 158 against 137.
            fixture.views().forEach { view -> assertEquals(21, view.whiteMaterialBalanceQuarterPoints) }

            fixture.play(toPromotion.last())
            assertEquals(piece("BLACK", "ROOK"), fixture.view(fixture.jordan).lastMove?.captured)
            // The new queen counts: 190 against 117.
            fixture.views().forEach { view -> assertEquals(73, view.whiteMaterialBalanceQuarterPoints) }

            // 5... Qxa8 takes the promoted queen.
            fixture.play("d8a8")
            fixture.views().forEach { view ->
                assertEquals(
                    listOf(
                        piece("BLACK", "PAWN"),
                        piece("BLACK", "PAWN"),
                        piece("BLACK", "BISHOP"),
                        piece("BLACK", "ROOK"),
                        piece("WHITE", "QUEEN"),
                    ),
                    captures(view).filterNotNull(),
                    view.yourSide,
                )
                // 154 against 117, +9.25; the captured-piece totals would say 41 − 36.
                assertEquals(37, view.whiteMaterialBalanceQuarterPoints, view.yourSide)
            }
        }
    }

    @Test
    fun undoRemovesTheCaptureAndRedoRestoresIt() {
        withGame { fixture ->
            fixture.play(*toPromotion.toTypedArray(), "d8a8")

            assertIs<CommandResult.Applied>(fixture.commands.undoMove(fixture.alex, fixture.gameId, fixture.game().version))
            fixture.views().forEach { view ->
                assertNull(captures(view).firstOrNull { it == piece("WHITE", "QUEEN") }, "the queen is back")
                assertEquals(piece("BLACK", "ROOK"), view.lastMove?.captured)
                assertEquals(73, view.whiteMaterialBalanceQuarterPoints)
            }

            assertIs<CommandResult.Applied>(fixture.commands.redo(fixture.alex, fixture.gameId, fixture.game().version))
            fixture.views().forEach { view ->
                assertEquals(piece("WHITE", "QUEEN"), view.lastMove?.captured)
                assertEquals(37, view.whiteMaterialBalanceQuarterPoints)
            }
        }
    }

    @Test
    fun theProjectionIsAdditiveAndOptional() {
        // An older server's move omits the captured piece; it still reads, as nothing captured.
        val move = Json.decodeFromString(MoveView.serializer(), """{"from":"e2","to":"e4","san":"e4"}""")
        assertNull(move.captured)

        val captured = MoveView("e4", "d5", null, "exd5", CapturedPieceView("BLACK", "PAWN"))
        assertEquals(
            """{"from":"e4","to":"d5","san":"exd5","captured":{"side":"BLACK","type":"PAWN"}}""",
            Json.encodeToString(MoveView.serializer(), captured),
        )
    }
}
