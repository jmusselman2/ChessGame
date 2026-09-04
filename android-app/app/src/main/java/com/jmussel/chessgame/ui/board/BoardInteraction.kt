package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.DrawClaim
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
 * A legal move the player has chosen but not played yet, held back because playing it
 * would throw away a draw only declaring it entitles (`D038`, `D041`).
 *
 * [claims] is what `game-core` grants for this exact declaration and nothing else; the
 * player either claims one of them or plays [move].
 */
data class DeclaredMove(
    val move: Move,
    val claims: Set<DrawClaim>,
)

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
     * The move the player has declared but not played, while they decide whether to claim
     * the draw it would entitle instead of playing it.
     */
    val declaredMove: DeclaredMove? = null,
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
     * promotion prompt instead of moving, because the player must choose the piece, and a
     * move that would entitle a draw claim is declared rather than played, because the
     * player must choose between the two. A finished game cannot be interacted with.
     */
    fun onSquareTapped(
        state: BoardUiState,
        square: Square,
    ): BoardUiState {
        val cleared = state.copy(selectedSquare = null, pendingPromotion = null, declaredMove = null)

        if (state.game.isOver) return cleared
        if (state.pendingPromotion != null) return cleared
        if (state.declaredMove != null) return cleared
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

    /**
     * The state after the player plays the move they declared, giving up the draw claim
     * declaring it entitled.
     */
    fun playDeclaredMove(state: BoardUiState): BoardUiState {
        val declared = requireNotNull(state.declaredMove) { "No move has been declared" }
        return applyMove(state, declared.move)
    }

    /** The state after the player backs out, having neither played nor claimed. */
    fun cancelDeclaredMove(state: BoardUiState): BoardUiState = state.copy(selectedSquare = null, declaredMove = null)

    private fun play(
        state: BoardUiState,
        from: Square,
        to: Square,
    ): BoardUiState {
        val moves = movesFrom(state, from).filter { it.to == to }
        val promotion = moves.any { it.promotion != null }

        if (promotion) return state.copy(pendingPromotion = PendingPromotion(from, to))

        val move = moves.single()
        val claims = prospectiveDrawClaims(state, move)

        return if (claims.isEmpty()) applyMove(state, move) else state.copy(declaredMove = DeclaredMove(move, claims))
    }

    /**
     * The draws only declaring [move] would entitle: what `game-core` allows once the move
     * is declared, less what may already be claimed without declaring anything.
     *
     * Playing the move throws those away — the position it makes is the other player's to
     * claim from — so a move carrying one is declared and the player asked (`D041`). The
     * claims the position already offers are not part of this: they are on screen already,
     * and they do not depend on the declaration.
     */
    private fun prospectiveDrawClaims(
        state: BoardUiState,
        move: Move,
    ): Set<DrawClaim> = ChessRules.availableDrawClaims(state.game, move) - ChessRules.availableDrawClaims(state.game)

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
