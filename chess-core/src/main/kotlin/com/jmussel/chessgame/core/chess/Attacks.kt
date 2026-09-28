package com.jmussel.chessgame.core.chess

/**
 * Which squares a position attacks, and whether a king stands in check.
 *
 * A square is attacked if a piece could capture an enemy piece standing there. That
 * includes squares occupied by the attacker's own pieces — those are defended, and a king
 * may not capture into them — and, for pawns, only the capture diagonals rather than the
 * squares they advance to.
 */
object Attacks {
    /** Every square the piece on [from] attacks. */
    fun attackedSquaresFrom(
        board: Board,
        from: Square,
    ): List<Square> {
        val piece = requireNotNull(board.pieceAt(from)) { "No piece on $from" }
        return when (piece.type) {
            PieceType.PAWN -> PseudoLegalMoves.pawnCaptureSquares(from, piece.side)
            PieceType.KNIGHT -> PseudoLegalMoves.KNIGHT_STEPS.mapNotNull { from.shifted(it) }
            PieceType.KING -> Direction.ALL.mapNotNull { from.shifted(it) }
            else ->
                requireNotNull(PseudoLegalMoves.slidingDirectionsFor(piece.type))
                    .flatMap { PseudoLegalMoves.ray(board, from, it) }
        }
    }

    /** Every square from which [side] attacks [square], in square order. */
    fun attackersOf(
        board: Board,
        square: Square,
        side: Side,
    ): List<Square> = board.squaresOf(side).filter { square in attackedSquaresFrom(board, it) }

    /** Whether [side] attacks [square]. */
    fun isAttacked(
        board: Board,
        square: Square,
        side: Side,
    ): Boolean = board.squaresOf(side).any { square in attackedSquaresFrom(board, it) }

    /** Every square [side] attacks, without duplicates, in square order. */
    fun attackedSquares(
        board: Board,
        side: Side,
    ): Set<Square> = board.squaresOf(side).flatMapTo(linkedSetOf()) { attackedSquaresFrom(board, it) }

    /** Where [side]'s king stands, or `null` when it is not on the board. */
    fun kingSquare(
        board: Board,
        side: Side,
    ): Square? = board.squaresOf(side, PieceType.KING).firstOrNull()

    /** Whether [side]'s king is currently attacked. */
    fun isInCheck(
        board: Board,
        side: Side,
    ): Boolean {
        val king = requireNotNull(kingSquare(board, side)) { "$side has no king on the board" }
        return isAttacked(board, king, side.opposite)
    }

    /** Whether [side]'s king is currently attacked in [state]. */
    fun isInCheck(
        state: GameState,
        side: Side,
    ): Boolean = isInCheck(state.board, side)

    /** Whether the side to move in [state] is in check. */
    fun isSideToMoveInCheck(state: GameState): Boolean = isInCheck(state.board, state.sideToMove)
}
