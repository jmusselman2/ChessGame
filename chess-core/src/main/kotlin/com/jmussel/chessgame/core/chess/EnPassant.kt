package com.jmussel.chessgame.core.chess

/**
 * En passant: a pawn that has just advanced two squares may be captured, for one move
 * only, as if it had advanced one.
 *
 * [GameState.enPassantTarget] holds the square the capturing pawn moves onto — the square
 * the double-advancing pawn skipped over. It is set by the double advance and cleared by
 * the very next move, so the right to capture expires immediately.
 *
 * The target is only a marker, and a marker can disagree with the board: a [GameState] may
 * be built directly or rebuilt from storage rather than produced by [ChessRules.applyMove].
 * Eligibility is therefore decided from the board rather than from the marker alone — the
 * target must stand empty on the rank the side to move captures onto, with the opposing
 * pawn that skipped it directly behind. Generation and recognition apply the same test, so
 * a move counts as an en passant capture exactly when it is one of the generated ones, and
 * an inconsistent marker can neither invent a capture nor remove a piece the move did not
 * take.
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
     * Whether [move] captures en passant in [state]: the side to move's pawn stepping one
     * square diagonally onto a target that really does describe a capturable pawn.
     */
    fun isCapture(
        state: GameState,
        move: Move,
    ): Boolean {
        if (move.to != state.enPassantTarget || move.promotion != null) return false
        if (capturablePawnSquare(state) == null) return false

        val side = state.sideToMove
        if (state.board.pieceAt(move.from) != Piece(side, PieceType.PAWN)) return false
        return move.to in PseudoLegalMoves.pawnCaptureSquares(move.from, side)
    }

    /**
     * The en passant captures available to the side to move, ignoring self-check, which
     * [LegalMoves] filters.
     */
    fun availableMoves(state: GameState): List<Move> {
        val target = state.enPassantTarget ?: return emptyList()
        if (capturablePawnSquare(state) == null) return emptyList()

        return state.board
            .squaresOf(state.sideToMove, PieceType.PAWN)
            .filter { target in PseudoLegalMoves.pawnCaptureSquares(it, state.sideToMove) }
            .map { Move(it, target) }
    }

    /**
     * The square holding the pawn [state]'s en passant target says may be captured, or
     * `null` when the target does not describe one.
     *
     * A target only describes a capturable pawn when it is on the rank the side to move
     * captures onto, nothing stands on it, and the pawn that skipped it — the opposing
     * pawn one square behind — is really there.
     */
    private fun capturablePawnSquare(state: GameState): Square? {
        val target = state.enPassantTarget ?: return null
        val side = state.sideToMove
        if (target.rank != targetRankOf(side)) return null
        if (!state.board.isEmpty(target)) return null

        val captured = Square.of(target.file, target.rank - PseudoLegalMoves.pawnAdvanceDirection(side).rankStep)
        return if (state.board.pieceAt(captured) == Piece(side.opposite, PieceType.PAWN)) captured else null
    }

    /**
     * The rank an en passant target stands on while [side] is to move: rank 6 for White and
     * rank 3 for Black — the square an opposing pawn skips on its double advance.
     */
    private fun targetRankOf(side: Side): Int =
        StandardPosition.pawnRankOf(side.opposite) + PseudoLegalMoves.pawnAdvanceDirection(side.opposite).rankStep
}
