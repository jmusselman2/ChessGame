package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.GameResult
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardInteractionTest {
    private val newGame = BoardUiState.newGame()

    private fun tap(
        state: BoardUiState,
        square: String,
    ): BoardUiState = BoardInteraction.onSquareTapped(state, Square.parse(square))

    @Test
    fun aNewBoardHasNothingSelected() {
        assertNull(newGame.selectedSquare)
        assertEquals(ChessGame.newGame(), newGame.game)
    }

    @Test
    fun tappingOwnPieceSelectsIt() {
        val selected = tap(newGame, "e2")

        assertEquals(Square.parse("e2"), selected.selectedSquare)
        assertTrue(BoardInteraction.isSelected(selected, Square.parse("e2")))
    }

    @Test
    fun onlyTheTappedSquareIsSelected() {
        val selected = tap(newGame, "e2")

        assertFalse(BoardInteraction.isSelected(selected, Square.parse("d2")))
        assertFalse(BoardInteraction.isSelected(selected, Square.parse("e4")))
    }

    @Test
    fun tappingTheSelectedSquareAgainClearsIt() {
        val cleared = tap(tap(newGame, "e2"), "e2")

        assertNull(cleared.selectedSquare)
    }

    @Test
    fun tappingAnotherOwnPieceMovesTheSelection() {
        val moved = tap(tap(newGame, "e2"), "g1")

        assertEquals(Square.parse("g1"), moved.selectedSquare)
    }

    @Test
    fun tappingAnEmptySquareSelectsNothing() {
        assertNull(tap(newGame, "e4").selectedSquare)
        assertNull(tap(tap(newGame, "e2"), "e4").selectedSquare)
    }

    @Test
    fun tappingAnOpponentPieceSelectsNothing() {
        assertNull(tap(newGame, "e7").selectedSquare)
        assertNull(tap(tap(newGame, "e2"), "e7").selectedSquare)
    }

    @Test
    fun theSelectableSideFollowsWhoseTurnItIs() {
        val afterWhiteMoves = BoardUiState(ChessRules.applyMove(ChessGame.newGame(), Move.of("e2", "e4")))

        assertEquals(Side.BLACK, afterWhiteMoves.game.sideToMove)
        assertEquals(Square.parse("e7"), tap(afterWhiteMoves, "e7").selectedSquare)
        assertNull(tap(afterWhiteMoves, "e4").selectedSquare)
    }

    @Test
    fun selectingDoesNotTouchTheGame() {
        val selected = tap(newGame, "e2")

        assertEquals(newGame.game, selected.game)
        assertTrue(selected.game.history.isEmpty())
    }

    @Test
    fun aFinishedGameCannotBeSelectedOn() {
        val played = ChessRules.applyMove(ChessGame.newGame(), Move.of("e2", "e4"))
        val finished =
            BoardUiState(
                game = played.copy(state = played.state.copy(result = GameResult.resignation(loser = Side.BLACK))),
                selectedSquare = Square.parse("e4"),
            )

        assertNull(BoardInteraction.onSquareTapped(finished, Square.parse("d2")).selectedSquare)
    }
}
