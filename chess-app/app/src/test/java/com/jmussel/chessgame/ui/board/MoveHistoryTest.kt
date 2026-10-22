package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.StandardPosition
import com.jmussel.chessgame.ui.review.GameReview
import com.jmussel.chessgame.ui.review.ReviewMove
import com.jmussel.chessgame.ui.review.ReviewPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The move list the game screens show (`D105`, `M23.2`): typed entries, numbered White and
 * Black pairs, exactly one current move whenever a move is shown, Review limited to the ply on
 * the board, and live games current at their last active move.
 */
class MoveHistoryTest {
    private fun played(
        vararg moves: String,
        from: ChessGame = ChessGame.newGame(),
    ): ChessGame = moves.fold(from) { game, move -> ChessRules.applyMove(game, Move.of(move.take(2), move.substring(2, 4))) }

    private fun assertOneCurrent(
        history: MoveHistory,
        san: String,
    ) {
        assertEquals(listOf(san), history.entries.filter { it.current }.map { it.san })
        assertEquals(san, history.current?.san)
    }

    // Review prefixes.

    private val sans = listOf("e4", "e5", "Nf3", "Nc6", "Bb5")

    @Test
    fun atReviewPlyZeroNothingIsShownAndNothingIsCurrent() {
        val history = MoveHistory.review(sans, ply = 0)

        assertTrue(history.rows.isEmpty())
        assertNull(history.current)
        assertNull(history.currentRow)
        assertTrue(history.lines.isEmpty())
    }

    @Test
    fun afterWhitesMoveItsRowHoldsWhitesMoveAloneAndBlacksReplyIsNotShown() {
        val history = MoveHistory.review(sans, ply = 3)

        assertEquals(listOf("1. e4 e5", "2. Nf3"), history.lines)
        val row = history.rows.last()
        assertNull("Black's 2... Nc6 is after the position on the board", row.black)
        assertEquals(MoveHistoryEntry(ply = 3, moveNumber = 2, side = Side.WHITE, san = "Nf3", current = true), row.white)
        assertTrue(history.entries.none { it.san == "Nc6" || it.san == "Bb5" })
        assertOneCurrent(history, "Nf3")
    }

    @Test
    fun afterBlacksMoveTheWholePairIsShownAndOnlyBlacksMoveIsCurrent() {
        val history = MoveHistory.review(sans, ply = 4)

        assertEquals(listOf("1. e4 e5", "2. Nf3 Nc6"), history.lines)
        val row = history.rows.last()
        assertEquals(false, row.white.current)
        assertEquals(MoveHistoryEntry(ply = 4, moveNumber = 2, side = Side.BLACK, san = "Nc6", current = true), row.black)
        assertEquals(1, history.currentRow)
        assertOneCurrent(history, "Nc6")
    }

    @Test
    fun atTheFinalPlyEveryMoveIsShownAndTheLastIsCurrent() {
        val history = MoveHistory.review(sans, ply = sans.size)

        assertEquals(listOf("1. e4 e5", "2. Nf3 Nc6", "3. Bb5"), history.lines)
        assertEquals(MoveHistory.live(sans), history)
        assertOneCurrent(history, "Bb5")
        assertEquals((1..5).toList(), history.entries.map { it.ply })
        assertEquals(listOf(1, 1, 2, 2, 3), history.entries.map { it.moveNumber })
        assertEquals(listOf(Side.WHITE, Side.BLACK, Side.WHITE, Side.BLACK, Side.WHITE), history.entries.map { it.side })
    }

    @Test
    fun everyReviewPlyHasExactlyOneCurrentMoveAndNoLaterMove() {
        (1..sans.size).forEach { ply ->
            val history = MoveHistory.review(sans, ply)

            assertEquals("ply $ply", sans.take(ply), history.entries.map { it.san })
            assertOneCurrent(history, sans[ply - 1])
        }
    }

    @Test
    fun aGameReviewShowsTheHistoryOfItsOwnPly() {
        val position = ReviewPosition(StandardPosition.BOARD, Side.WHITE, inCheck = false)
        val review =
            GameReview(
                title = "Against Alex",
                moves = sans.map { ReviewMove(Square.parse("a1"), Square.parse("a2"), it) },
                positions = List(sans.size + 1) { position },
                ending = "White won",
                orientation = Side.WHITE,
            )

        assertOneCurrent(review.history, "Bb5")
        assertTrue(
            "Start",
            review
                .at(0)
                .history.rows
                .isEmpty(),
        )
        assertEquals(listOf("1. e4"), review.at(1).moveListLines())
        assertOneCurrent(review.at(2).history, "e5")
        assertEquals(MoveHistory.review(sans, 3), review.at(4).at(3).history)
    }

    // Live games.

    @Test
    fun aGameWithNoMovesHasNoCurrentMove() {
        val history = GameControls.moveHistory(ChessGame.newGame())

        assertTrue(history.rows.isEmpty())
        assertNull(history.current)
    }

    @Test
    fun aLocalMoveBecomesCurrent() {
        assertOneCurrent(GameControls.moveHistory(played("e2e4")), "e4")
        assertOneCurrent(GameControls.moveHistory(played("e2e4", "e7e5")), "e5")
        assertEquals(listOf("1. e4 e5", "2. Nf3"), GameControls.moveListLines(played("e2e4", "e7e5", "g1f3")))
    }

    @Test
    fun undoDropsTheMoveAndTheNewLastMoveIsCurrent() {
        val undone = ChessRules.undo(played("e2e4", "e7e5", "g1f3"), Side.WHITE)
        val history = GameControls.moveHistory(undone)

        assertEquals(listOf("1. e4 e5"), history.lines)
        assertOneCurrent(history, "e5")
        assertNull(
            "nothing is current once every move is undone",
            GameControls.moveHistory(ChessRules.undo(played("e2e4"), Side.WHITE)).current,
        )
    }

    @Test
    fun redoAndAMatchingContinuationRestoreTheRecordedMoveAsCurrent() {
        val undone = ChessRules.undo(played("e2e4", "e7e5", "g1f3"), Side.WHITE)

        assertOneCurrent(GameControls.moveHistory(ChessRules.redo(undone)), "Nf3")
        assertOneCurrent(GameControls.moveHistory(ChessRules.applyMove(undone, Move.of("g1", "f3"))), "Nf3")
    }

    @Test
    fun aComputerReplyAndATwoPlyTakebackMoveTheCurrentMove() {
        // The computer game shows its board's `ChessGame` the same way: a reply is current,
        // and a takeback of a move and its reply leaves the move before them current.
        val replied = played("e2e4", "e7e5", "g1f3", "b8c6")
        assertOneCurrent(GameControls.moveHistory(replied), "Nc6")

        val takenBack = ChessRules.undoLastMoves(replied, plies = 2)
        assertOneCurrent(GameControls.moveHistory(takenBack), "e5")
        assertOneCurrent(GameControls.moveHistory(ChessRules.redo(takenBack)), "Nc6")
    }

    @Test
    fun aFinishedGameKeepsItsLastMoveCurrent() {
        // 1. f3 e5 2. g4 Qh4#
        val mated = played("f2f3", "e7e5", "g2g4", "d8h4")

        assertTrue(mated.isOver)
        assertOneCurrent(GameControls.moveHistory(mated), "Qh4#")
    }
}
