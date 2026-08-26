package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.GameOutcome
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.TerminationReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whole games played the way a player plays them: tap the piece, tap the destination, one
 * device, both sides.
 */
class LocalGameTest {
    private fun tap(
        state: BoardUiState,
        vararg squares: String,
    ): BoardUiState {
        var current = state
        squares.forEach { current = BoardInteraction.onSquareTapped(current, Square.parse(it)) }
        return current
    }

    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    @Test
    fun aCompleteGameCanBePlayedToCheckmate() {
        var state = BoardUiState.newGame()

        // Scholar's mate, tapped out one square at a time.
        state = tap(state, "e2", "e4")
        state = tap(state, "e7", "e5")
        state = tap(state, "f1", "c4")
        state = tap(state, "b8", "c6")
        state = tap(state, "d1", "h5")
        state = tap(state, "g8", "f6")
        state = tap(state, "h5", "f7")

        assertTrue(state.game.isOver)
        assertEquals(GameOutcome.WHITE_WINS, state.game.result?.outcome)
        assertEquals(TerminationReason.CHECKMATE, state.game.result?.reason)
        assertEquals(white(PieceType.QUEEN), state.board.pieceAt(Square.parse("f7")))
        assertEquals(7, state.game.moves.size)
        assertEquals(
            listOf("1. e2e4 e7e5", "2. f1c4 b8c6", "3. d1h5 g8f6", "4. h5f7"),
            GameControls.moveListLines(state.game),
        )
    }

    @Test
    fun theBoardTurnsToWhoeverIsToMoveThroughoutTheGame() {
        var state = BoardUiState.newGame()

        assertEquals(Side.WHITE, state.orientation)

        state = tap(state, "e2", "e4")
        assertEquals(Side.BLACK, state.orientation)

        state = tap(state, "e7", "e5")
        assertEquals(Side.WHITE, state.orientation)
    }

    @Test
    fun aTakenBackMoveCanBeReplacedWithAnotherAndTheGameCarriesOn() {
        var state = tap(BoardUiState.newGame(), "e2", "e4", "e7", "e5")

        assertTrue(GameControls.canUndo(state))
        state = GameControls.undo(state)

        assertEquals(1, state.game.moves.size)
        assertEquals(Side.BLACK, state.game.sideToMove)

        state = tap(state, "c7", "c5")
        state = tap(state, "g1", "f3")

        assertEquals(3, state.game.moves.size)
        assertEquals(listOf("1. e2e4 c7c5", "2. g1f3"), GameControls.moveListLines(state.game))
    }

    @Test
    fun aFinishedGameStopsAcceptingTaps() {
        var state = BoardUiState.newGame()
        state = tap(state, "f2", "f3", "e7", "e5", "g2", "g4", "d8", "h4")

        assertTrue(state.game.isOver)
        assertEquals(GameOutcome.BLACK_WINS, state.game.result?.outcome)

        val afterMoreTaps = tap(state, "e1", "f2", "h4", "g4")

        assertEquals(state.game, afterMoreTaps.game)
        assertFalse(GameControls.canUndo(afterMoreTaps))
        assertFalse(GameControls.canClaimDraw(afterMoreTaps))
    }

    @Test
    fun aGameCanBeFinishedByClaimingARepetitionDraw() {
        var state = BoardUiState.newGame()

        repeat(2) {
            state = tap(state, "g1", "f3", "g8", "f6", "f3", "g1", "f6", "g8")
        }

        assertFalse(state.game.isOver)
        assertTrue(GameControls.canClaimDraw(state))

        state = GameControls.claimDraw(state, DrawClaim.THREEFOLD_REPETITION)

        assertTrue(state.game.isOver)
        assertEquals(GameOutcome.DRAW, state.game.result?.outcome)
        assertEquals(TerminationReason.THREEFOLD_REPETITION_CLAIM, state.game.result?.reason)
        assertEquals(8, state.game.moves.size)
    }

    @Test
    fun aPawnCanRunTheBoardAndPromote() {
        var state = BoardUiState.newGame()

        listOf(
            "a2" to "a4",
            "b7" to "b5",
            "a4" to "b5",
            "b8" to "c6",
            "b5" to "b6",
            "c6" to "d4",
            "b6" to "b7",
            "d4" to "c6",
        ).forEach { (from, to) -> state = tap(state, from, to) }

        state = tap(state, "b7", "a8")

        assertEquals(Square.parse("a8"), state.pendingPromotion?.to)

        state = BoardInteraction.choosePromotion(state, PieceType.QUEEN)

        assertEquals(white(PieceType.QUEEN), state.board.pieceAt(Square.parse("a8")))
        assertFalse(state.game.isOver)
        assertEquals(Side.BLACK, state.game.sideToMove)
    }

    @Test
    fun castlingCanBePlayedInARealOpening() {
        var state = BoardUiState.newGame()

        listOf(
            "e2" to "e4",
            "e7" to "e5",
            "g1" to "f3",
            "b8" to "c6",
            "f1" to "c4",
            "f8" to "c5",
        ).forEach { (from, to) -> state = tap(state, from, to) }

        state = tap(state, "e1", "g1")

        assertEquals(white(PieceType.KING), state.board.pieceAt(Square.parse("g1")))
        assertEquals(white(PieceType.ROOK), state.board.pieceAt(Square.parse("f1")))
        assertEquals("4. e1g1", GameControls.moveListLines(state.game).last())
    }

    @Test
    fun everyTapGoesThroughGameCoreSoTheHistoryStaysComplete() {
        var state = BoardUiState.newGame()
        listOf("e2" to "e4", "e7" to "e5", "g1" to "f3", "b8" to "c6").forEach { (from, to) ->
            state = tap(state, from, to)
        }

        assertEquals(4, state.game.history.size)

        var unwound = state
        repeat(4) { unwound = GameControls.undo(unwound) }

        assertEquals(BoardUiState.newGame().game, unwound.game)
    }
}
