package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Square

/**
 * What the board screen is showing: the game itself, plus the purely local selection the
 * player has made.
 *
 * The selection is UI state — it never reaches `game-core` and is not part of the game.
 */
data class BoardUiState(
    val game: ChessGame,
    val selectedSquare: Square? = null,
) {
    /** The position being drawn. */
    val board: Board
        get() = game.state.board

    companion object {
        fun newGame(): BoardUiState = BoardUiState(ChessGame.newGame())
    }
}

/**
 * How tapping a square changes the board screen.
 *
 * Kept out of Compose so it can be tested on its own, and kept out of `game-core` because
 * selecting a piece is not a chess rule.
 */
object BoardInteraction {
    /**
     * The state after the player taps [square].
     *
     * Tapping one of the moving side's pieces selects it; tapping it again clears the
     * selection, as does tapping anywhere else. A finished game cannot be interacted with.
     */
    fun onSquareTapped(
        state: BoardUiState,
        square: Square,
    ): BoardUiState {
        if (state.game.isOver) return state.copy(selectedSquare = null)
        if (square == state.selectedSquare) return state.copy(selectedSquare = null)

        val piece = state.board.pieceAt(square)
        val isOwnPiece = piece != null && piece.side == state.game.sideToMove

        return state.copy(selectedSquare = if (isOwnPiece) square else null)
    }

    /** Whether [square] is the one the player has selected. */
    fun isSelected(
        state: BoardUiState,
        square: Square,
    ): Boolean = state.selectedSquare == square

    /**
     * Where the selected piece may legally move, or an empty set when nothing is selected.
     *
     * The moves come from `game-core`; the UI never works out for itself where a piece may
     * go. A pawn reaching the last rank yields one square, not one square per promotion
     * choice.
     */
    fun legalDestinations(state: BoardUiState): Set<Square> {
        val from = state.selectedSquare ?: return emptySet()
        return ChessRules
            .legalMoves(state.game)
            .filter { it.from == from }
            .mapTo(mutableSetOf()) { it.to }
    }

    /** Whether [square] is somewhere the selected piece may move to. */
    fun isLegalDestination(
        state: BoardUiState,
        square: Square,
    ): Boolean = square in legalDestinations(state)
}
