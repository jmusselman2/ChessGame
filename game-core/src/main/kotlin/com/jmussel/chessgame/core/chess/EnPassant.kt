package com.jmussel.chessgame.core.chess

/**
 * En passant: a pawn that has just advanced two squares may be captured, for one move
 * only, as if it had advanced one.
 *
 * [GameState.enPassantTarget] holds the square the capturing pawn moves onto — the square
 * the double-advancing pawn skipped over. It is set by the double advance and cleared by
 * the very next move, so the right to capture expires immediately.
 */
object EnPassant {
    /**
     * The en passant target square created by [move] on [board], or `null` when [move] is
     * not a two-square pawn advance.
     */
    fun targetAfter(
        board: Board,
        move: Move,
    ): Square? {
        val piece = board.pieceAt(move.from) ?: return null
        if (piece.type != PieceType.PAWN) return null
        if (kotlin.math.abs(move.to.rank - move.from.rank) != 2) return null
        return Square.of(move.from.file, (move.from.rank + move.to.rank) / 2)
    }

    /** The square holding the pawn captured by the en passant [move]. */
    fun capturedPawnSquare(move: Move): Square = Square.of(move.to.file, move.from.rank)

    /**
     * Whether [move] captures en passant in [state]: a pawn moving diagonally onto the
     * current en passant target.
     */
    fun isCapture(
        state: GameState,
        move: Move,
    ): Boolean {
        val target = state.enPassantTarget ?: return false
        if (move.to != target) return false
        val piece = state.board.pieceAt(move.from) ?: return false
        return piece.type == PieceType.PAWN && move.from.file != move.to.file
    }

    /**
     * The en passant captures available to the side to move, ignoring self-check, which
     * [LegalMoves] filters.
     */
    fun availableMoves(state: GameState): List<Move> {
        val target = state.enPassantTarget ?: return emptyList()
        val side = state.sideToMove
        if (!state.board.isEmpty(target)) return emptyList()

        return state.board
            .squaresOf(side, PieceType.PAWN)
            .filter { target in PseudoLegalMoves.pawnCaptureSquares(it, side) }
            .map { Move(it, target) }
    }
}
