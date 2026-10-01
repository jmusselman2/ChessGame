package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.Square
import org.junit.Assert.assertEquals
import org.junit.Test

/** The pass-and-play last-move highlight, read from `chess-core`'s move history. */
class LastMoveHighlightTest {
    private fun tap(
        state: BoardUiState,
        vararg squares: String,
    ): BoardUiState =
        squares.fold(state) { current, square ->
            BoardInteraction.onSquareTapped(current, Square.parse(square))
        }

    private fun highlighted(state: BoardUiState): Set<String> = BoardRendering.lastMoveSquares(state.game).map { it.name }.toSet()

    @Test
    fun nothingIsHighlightedBeforeTheFirstMove() {
        assertEquals(emptySet<String>(), highlighted(BoardUiState.newGame()))
    }

    @Test
    fun theMoveJustPlayedIsHighlightedFromAndTo() {
        val played = tap(BoardUiState.newGame(), "e2", "e4")

        assertEquals(setOf("e2", "e4"), highlighted(played))
    }

    @Test
    fun theReplyReplacesTheHighlight() {
        val played = tap(BoardUiState.newGame(), "e2", "e4", "e7", "e5")

        assertEquals(setOf("e7", "e5"), highlighted(played))
    }

    @Test
    fun afterUndoThePreviousMoveIsHighlighted() {
        val played = tap(BoardUiState.newGame(), "e2", "e4", "e7", "e5")

        assertEquals(setOf("e2", "e4"), highlighted(GameControls.undo(played)))
    }

    @Test
    fun undoingTheOnlyMoveClearsTheHighlight() {
        val played = tap(BoardUiState.newGame(), "e2", "e4")

        assertEquals(emptySet<String>(), highlighted(GameControls.undo(played)))
    }
}
