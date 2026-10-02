package com.jmussel.chessgame.ai

import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.CastlingRights
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.StandardPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The project's engine against `D086`'s contract (`M21.5`).
 *
 * No test pins one exact move where several are reasonable: they check that the move is
 * legal, or that it is the one move that wins (a mate, a free queen).
 */
class AlphaBetaEngineTest {
    private val engine = AlphaBetaEngine(seed = 1)

    /**
     * A position from the placement and side-to-move fields of FEN, with no castling and no
     * en passant: `"6k1/5ppp/8/8/8/8/5PPP/R5K1 w"`.
     */
    private fun position(fen: String): GameState {
        val (placement, side) = fen.split(" ")
        val pieces = mutableMapOf<Square, Piece>()
        placement.split("/").forEachIndexed { row, text ->
            var file = 0
            text.forEach { symbol ->
                if (symbol.isDigit()) {
                    file += symbol.digitToInt()
                } else {
                    pieces[Square.of(file, Square.RANKS - 1 - row)] = Piece.fromSymbol(symbol)
                    file++
                }
            }
        }
        return GameState(
            board = Board.of(pieces),
            sideToMove = if (side == "w") Side.WHITE else Side.BLACK,
            castlingRights = CastlingRights.NONE,
        )
    }

    private fun play(
        state: GameState,
        move: Move,
    ): GameState = ChessRules.applyMove(state, move)

    // --- Legal moves ----------------------------------------------------------------------

    @Test
    fun everyMoveIsLegalThroughWholeGamesAtEveryLevel() {
        Difficulty.entries.forEach { difficulty ->
            listOf(1L, 2L).forEach { seed ->
                val player = AlphaBetaEngine(seed = seed)
                var state = StandardPosition.newGame()
                repeat(if (difficulty == Difficulty.HARD) 12 else 40) {
                    if (state.isOver) return@repeat
                    val move = assertNotNull(player.chooseMove(state, difficulty))
                    assertTrue(ChessRules.isLegal(state, move), "$difficulty played illegal $move in\n${state.board}")
                    state = play(state, move)
                }
            }
        }
    }

    @Test
    fun theOnlyLegalMoveIsPlayed() {
        // The king is checked by an unguarded queen and can only take it.
        val state = position("k7/8/8/8/8/8/1q6/K7 w")
        assertEquals(listOf(Move.of("a1", "b2")), ChessRules.legalMoves(state))

        Difficulty.entries.forEach { assertEquals(Move.of("a1", "b2"), engine.chooseMove(state, it)) }
    }

    @Test
    fun aPawnOnTheSeventhPromotesBeforeTheKingTakesIt() {
        // The defending king attacks the pawn but not the square in front of it: promote now,
        // or lose the pawn.
        val white = position("8/P7/1k6/8/8/8/8/4K3 w")
        val black = position("4k3/8/8/8/8/1K6/p7/8 b")

        Difficulty.entries.forEach { difficulty ->
            listOf(white, black).forEach { state ->
                val move = assertNotNull(engine.chooseMove(state, difficulty))
                assertTrue(ChessRules.isLegal(state, move))
                if (difficulty != Difficulty.EASY) {
                    assertNotNull(move.promotion, "$difficulty did not promote: $move")
                }
            }
        }
        assertEquals(PieceType.QUEEN, engine.chooseMove(white, Difficulty.HARD)!!.promotion)
        assertEquals(PieceType.QUEEN, engine.chooseMove(black, Difficulty.HARD)!!.promotion)
    }

    // --- Strength -------------------------------------------------------------------------

    @Test
    fun mateInOneIsFoundAtEveryLevel() {
        val white = position("6k1/5ppp/8/8/8/8/5PPP/R5K1 w")
        val black = position("r5k1/5ppp/8/8/8/8/5PPP/6K1 b")

        Difficulty.entries.forEach { difficulty ->
            listOf(white, black).forEach { state ->
                val move = assertNotNull(engine.chooseMove(state, difficulty))
                assertNotNull(play(state, move).result, "$difficulty missed the mate with $move")
            }
        }
    }

    @Test
    fun theHigherLevelsTakeAHangingQueen() {
        val state = position("4k3/8/8/3q4/8/2N5/8/4K3 w")

        listOf(Difficulty.MEDIUM, Difficulty.HARD).forEach { difficulty ->
            listOf(1L, 2L, 3L).forEach { seed ->
                assertEquals(Move.of("c3", "d5"), AlphaBetaEngine(seed = seed).chooseMove(state, difficulty), "$difficulty, seed $seed")
            }
        }
    }

    @Test
    fun theHigherLevelsDoNotTakeAPieceThatIsGuardedWithTheQueen() {
        // Taking the knight with the queen loses the queen to the pawn.
        val state = position("4k3/8/4p3/3n4/8/8/3Q4/4K3 w")

        listOf(Difficulty.MEDIUM, Difficulty.HARD).forEach { difficulty ->
            assertTrue(engine.chooseMove(state, difficulty) != Move.of("d2", "d5"), "$difficulty gave the queen away")
        }
    }

    // --- Determinism ----------------------------------------------------------------------

    @Test
    fun oneSeedAlwaysPlaysTheSameMoveInTheSamePosition() {
        Difficulty.entries.forEach { difficulty ->
            var state = StandardPosition.newGame()
            repeat(if (difficulty == Difficulty.HARD) 6 else 16) {
                val first = AlphaBetaEngine(seed = 7).chooseMove(state, difficulty)
                val second = AlphaBetaEngine(seed = 7).chooseMove(state, difficulty)
                assertEquals(first, second, "$difficulty in\n${state.board}")
                state = play(state, first!!)
            }
        }
    }

    @Test
    fun theEasiestLevelVariesWithTheSeed() {
        val start = StandardPosition.newGame()
        val openings = (1L..20L).map { AlphaBetaEngine(seed = it).chooseMove(start, Difficulty.EASY) }.toSet()

        assertTrue(openings.size > 1, "Every seed opened with $openings")
    }

    // --- Time, cancellation and finished games -------------------------------------------

    @Test
    fun aNormalSearchFinishesWithinItsBudget() {
        val middlegame =
            listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "d2d3", "f8c5", "e1g1", "d7d6")
                .fold(StandardPosition.newGame()) { state, text -> play(state, Move.of(text.substring(0, 2), text.substring(2, 4))) }

        Difficulty.entries.forEach { difficulty ->
            listOf(StandardPosition.newGame(), middlegame).forEach { state ->
                val started = System.nanoTime()
                assertNotNull(engine.chooseMove(state, difficulty))
                val tookMillis = (System.nanoTime() - started) / 1_000_000
                val budget = AlphaBetaEngine.settingsFor(difficulty).budgetMillis
                println("$difficulty: ${tookMillis}ms of $budget")
                assertTrue(tookMillis <= budget + SLACK_MILLIS, "$difficulty took ${tookMillis}ms against a budget of ${budget}ms")
            }
        }
    }

    @Test
    fun aSpentBudgetStillYieldsALegalMove() {
        // A clock that jumps past every deadline as soon as it is read a second time.
        var reads = 0
        val impatient = AlphaBetaEngine(seed = 1, clock = { if (reads++ == 0) 0L else Long.MAX_VALUE / 2 })
        val start = StandardPosition.newGame()

        val move = assertNotNull(impatient.chooseMove(start, Difficulty.HARD))
        assertTrue(ChessRules.isLegal(start, move))
    }

    @Test
    fun aCancelledSearchHasNoMoveToApply() {
        val start = StandardPosition.newGame()
        // The clock stands still, so only cancelling can stop the search: on a busy machine
        // the real budget could run out first, and the search would rightly answer.
        val timeless = AlphaBetaEngine(seed = 1, clock = { 0L })

        assertNull(timeless.chooseMove(start, Difficulty.HARD) { true })

        // Cancelled part-way, after it has already settled on a fallback and searched a little.
        var polls = 0
        assertNull(timeless.chooseMove(start, Difficulty.HARD) { ++polls > 200 })
    }

    @Test
    fun aFinishedGameIsRefused() {
        val mated =
            listOf("f2f3", "e7e5", "g2g4", "d8h4")
                .fold(ChessGame.newGame()) { game, text -> ChessRules.applyMove(game, Move.of(text.substring(0, 2), text.substring(2, 4))) }
        assertTrue(mated.isOver)

        Difficulty.entries.forEach { difficulty ->
            assertFailsWith<IllegalArgumentException> { engine.chooseMove(mated.state, difficulty) }
        }
    }

    @Test
    fun difficultiesAreStoredAsTheirLevels() {
        assertEquals(listOf(1, 2, 3), Difficulty.entries.map { it.level })
        Difficulty.entries.forEach { assertEquals(it, Difficulty.ofLevel(it.level)) }
        assertFailsWith<IllegalArgumentException> { Difficulty.ofLevel(4) }
    }

    private companion object {
        /** What a busy CI machine may add to a search that stops on time. */
        const val SLACK_MILLIS = 750L
    }
}
