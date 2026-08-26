package com.jmussel.chessgame.core.chess

/**
 * The standard automatic insufficient-material draws.
 *
 * Only material combinations from which no sequence of legal moves can produce checkmate
 * count. That is:
 *
 * - king versus king,
 * - king and one bishop versus king,
 * - king and one knight versus king,
 * - king and bishop versus king and bishop with both bishops on the same square colour.
 *
 * Anything else — a pawn, a rook, a queen, two knights, bishop versus knight, or bishops
 * on opposite colours — leaves a mate possible with cooperative play and is therefore not
 * an automatic draw.
 */
object InsufficientMaterial {
    /** Whether [board] holds too little material for either side to deliver checkmate. */
    fun isDraw(board: Board): Boolean {
        val pieces = board.occupiedSquares().filter { (_, piece) -> piece.type != PieceType.KING }

        if (pieces.any { (_, piece) -> piece.type in MATING_MATERIAL }) return false
        if (pieces.size > 2) return false
        if (pieces.size <= 1) return true

        val (first, second) = pieces
        if (first.second.type != PieceType.BISHOP || second.second.type != PieceType.BISHOP) return false
        if (first.second.side == second.second.side) return false

        return squareColour(first.first) == squareColour(second.first)
    }

    /** Whether [state] has reached an insufficient-material draw. */
    fun isDraw(state: GameState): Boolean = isDraw(state.board)

    /** `0` for one set of squares and `1` for the other; bishops never change theirs. */
    private fun squareColour(square: Square): Int = (square.file + square.rank) % 2

    private val MATING_MATERIAL = setOf(PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN)
}
