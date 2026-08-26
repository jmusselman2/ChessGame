package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square

/**
 * A promotion the player has started but not finished: the pawn move is settled, the piece
 * to promote to is not.
 */
data class PendingPromotion(
    val from: Square,
    val to: Square,
) {
    /** The choices to offer, in conventional preference order. */
    val choices: List<PieceType>
        get() = PieceType.PROMOTION_CHOICES
}

/**
 * What the board screen is showing: the game itself, plus the purely local selection and
 * promotion prompt the player is working through.
 *
 * Both are UI state — they never reach `game-core` and are not part of the game.
 */
data class BoardUiState(
    val game: ChessGame,
    val selectedSquare: Square? = null,
    val pendingPromotion: PendingPromotion? = null,
    /**
     * Whose side of the board is drawn at the bottom.
     *
     * Pass-and-play on one device means the player at the board changes every move, so the
     * board turns to the side to move and each of them sees their own pieces nearest.
     * Once a player has a fixed colour in a multiplayer game, this is set to that colour
     * instead.
     */
    val orientation: Side = Side.WHITE,
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
 * selecting a piece is not a chess rule. Every move it plays goes through
 * [ChessRules.applyMove].
 */
object BoardInteraction {
    /**
     * The state after the player taps [square].
     *
     * Tapping one of the moving side's pieces selects it, and tapping one of that piece's
     * legal destinations plays the move. Tapping the selected square again clears the
     * selection, as does tapping anywhere else. A pawn reaching the last rank raises a
     * promotion prompt instead of moving, because the player must choose the piece.
     * A finished game cannot be interacted with.
     */
    fun onSquareTapped(
        state: BoardUiState,
        square: Square,
    ): BoardUiState {
        val cleared = state.copy(selectedSquare = null, pendingPromotion = null)

        if (state.game.isOver) return cleared
        if (state.pendingPromotion != null) return cleared
        if (square == state.selectedSquare) return cleared

        val from = state.selectedSquare
        if (from != null && square in legalDestinations(state)) {
            return play(state, from, square)
        }

        val piece = state.board.pieceAt(square)
        val isOwnPiece = piece != null && piece.side == state.game.sideToMove

        return cleared.copy(selectedSquare = if (isOwnPiece) square else null)
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
        return movesFrom(state, from).mapTo(mutableSetOf()) { it.to }
    }

    /** Whether [square] is somewhere the selected piece may move to. */
    fun isLegalDestination(
        state: BoardUiState,
        square: Square,
    ): Boolean = square in legalDestinations(state)

    /** The state after the player picks [choice] for the pending promotion. */
    fun choosePromotion(
        state: BoardUiState,
        choice: PieceType,
    ): BoardUiState {
        val pending = requireNotNull(state.pendingPromotion) { "No promotion is pending" }
        return applyMove(state, Move(pending.from, pending.to, choice))
    }

    /** The state after the player backs out of the promotion prompt. */
    fun cancelPromotion(state: BoardUiState): BoardUiState = state.copy(pendingPromotion = null)

    /** The state with the board turned around, so the other side is at the bottom. */
    fun flipBoard(state: BoardUiState): BoardUiState = state.copy(orientation = state.orientation.opposite)

    private fun play(
        state: BoardUiState,
        from: Square,
        to: Square,
    ): BoardUiState {
        val moves = movesFrom(state, from).filter { it.to == to }
        val promotion = moves.any { it.promotion != null }

        return if (promotion) {
            state.copy(pendingPromotion = PendingPromotion(from, to))
        } else {
            applyMove(state, moves.single())
        }
    }

    private fun applyMove(
        state: BoardUiState,
        move: Move,
    ): BoardUiState {
        val played = ChessRules.applyMove(state.game, move)
        return BoardUiState(
            game = played,
            selectedSquare = null,
            pendingPromotion = null,
            orientation = played.sideToMove,
        )
    }

    private fun movesFrom(
        state: BoardUiState,
        from: Square,
    ): List<Move> = ChessRules.legalMoves(state.game).filter { it.from == from }
}
