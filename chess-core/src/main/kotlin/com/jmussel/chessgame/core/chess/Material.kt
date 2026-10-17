package com.jmussel.chessgame.core.chess

/**
 * Material on the project owner's scale (`D104`): queen 9, rook 5, bishop 3.25, knight 3 and
 * pawn 1. Values are kept in exact quarter-pawn units — 36, 20, 13, 12 and 4 — so a bishop's
 * quarter point is never rounded away. A king has no material value.
 *
 * Display only: nothing in the rules decides anything by material. [ChessRules.materialBalance]
 * is the entry point.
 */
object Material {
    /** Quarter points in one pawn. */
    const val QUARTER_POINTS_PER_PAWN = 4

    /** What one piece of [type] is worth, in quarter points. */
    fun quarterPoints(type: PieceType): Int =
        when (type) {
            PieceType.QUEEN -> 36
            PieceType.ROOK -> 20
            PieceType.BISHOP -> 13
            PieceType.KNIGHT -> 12
            PieceType.PAWN -> 4
            PieceType.KING -> 0
        }

    /** [side]'s live material on [board], in quarter points. */
    fun total(
        board: Board,
        side: Side,
    ): Int = board.occupiedSquares().filter { (_, piece) -> piece.side == side }.sumOf { (_, piece) -> quarterPoints(piece.type) }
}
