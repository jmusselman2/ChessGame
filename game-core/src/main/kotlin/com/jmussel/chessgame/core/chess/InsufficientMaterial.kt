package com.jmussel.chessgame.core.chess

/**
 * The standard automatic insufficient-material draws: positions in which no sequence of
 * legal moves can produce checkmate for either side.
 *
 * A pawn, a rook, or a queen always leaves mate possible. Once none of those remain, only
 * two shapes are dead:
 *
 * - at most one piece besides the two kings — king versus king, and king with a single
 *   bishop or knight against a bare king,
 * - bishops only, every one of them on the same square colour, whatever the count and
 *   whoever owns them.
 *
 * The second is the general rule rather than a two-bishop special case. A bishop never
 * leaves its square colour, so bishops confined to one colour complex can only ever check
 * a king standing on that same colour, and that king always keeps neighbouring squares of
 * the other colour, which no bishop attacks, no piece here can occupy, and the opposing
 * king cannot cover without standing next to it. Promotion can produce any number of
 * same-coloured bishops on either side, and every one of those positions is dead.
 *
 * Anything else — bishops on both colours, or any knight once a second piece remains —
 * leaves a mate possible with cooperative play, and so is not an automatic draw.
 */
object InsufficientMaterial {
    /** Whether [board] holds too little material for either side to deliver checkmate. */
    fun isDraw(board: Board): Boolean {
        val remaining = board.occupiedSquares().filter { (_, piece) -> piece.type != PieceType.KING }

        if (remaining.any { (_, piece) -> piece.type in MATING_MATERIAL }) return false
        if (remaining.size <= 1) return true
        if (remaining.any { (_, piece) -> piece.type != PieceType.BISHOP }) return false

        return remaining.distinctBy { (square, _) -> squareColour(square) }.size == 1
    }

    /** Whether [state] has reached an insufficient-material draw. */
    fun isDraw(state: GameState): Boolean = isDraw(state.board)

    /** `0` for one set of squares and `1` for the other; bishops never change theirs. */
    private fun squareColour(square: Square): Int = (square.file + square.rank) % 2

    private val MATING_MATERIAL = setOf(PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN)
}
