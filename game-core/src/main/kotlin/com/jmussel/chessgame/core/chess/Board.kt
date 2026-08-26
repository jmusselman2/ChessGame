package com.jmussel.chessgame.core.chess

/**
 * An immutable piece placement over the 64 squares.
 *
 * The board knows only where pieces stand. Whose turn it is, castling rights, and every
 * other rule-tracking concern belongs to [GameState].
 */
class Board private constructor(
    private val squares: List<Piece?>,
) {
    fun pieceAt(square: Square): Piece? = squares[square.index]

    fun isEmpty(square: Square): Boolean = squares[square.index] == null

    /** Returns a copy with [piece] on [square], replacing whatever stood there. */
    fun withPiece(
        square: Square,
        piece: Piece,
    ): Board = with(square, piece)

    /** Returns a copy with [square] emptied. */
    fun withoutPiece(square: Square): Board = with(square, null)

    /** Returns a copy with [square] set to [piece], or emptied when [piece] is `null`. */
    fun with(
        square: Square,
        piece: Piece?,
    ): Board {
        if (squares[square.index] == piece) return this
        val updated = squares.toMutableList()
        updated[square.index] = piece
        return Board(updated)
    }

    /** Every occupied square with its piece, in square order. */
    fun occupiedSquares(): List<Pair<Square, Piece>> = Square.ALL.mapNotNull { square -> squares[square.index]?.let { square to it } }

    /** Every square occupied by [side], in square order. */
    fun squaresOf(side: Side): List<Square> = Square.ALL.filter { squares[it.index]?.side == side }

    /** Every square holding [side]'s [type], in square order. */
    fun squaresOf(
        side: Side,
        type: PieceType,
    ): List<Square> =
        Square.ALL.filter {
            val piece = squares[it.index]
            piece != null && piece.side == side && piece.type == type
        }

    val pieceCount: Int
        get() = squares.count { it != null }

    override fun equals(other: Any?): Boolean = this === other || (other is Board && squares == other.squares)

    override fun hashCode(): Int = squares.hashCode()

    /**
     * Renders the board from rank 8 down to rank 1, using `.` for an empty square.
     */
    override fun toString(): String =
        (Square.RANKS - 1 downTo 0).joinToString("\n") { rank ->
            (0 until Square.FILES).joinToString("") { file ->
                (squares[rank * Square.FILES + file]?.symbol ?: '.').toString()
            }
        }

    companion object {
        /** A board with no pieces on it. */
        val EMPTY: Board = Board(List(Square.COUNT) { null })

        /** Builds a board from an explicit placement. */
        fun of(placement: Map<Square, Piece>): Board {
            if (placement.isEmpty()) return EMPTY
            val squares = arrayOfNulls<Piece>(Square.COUNT)
            placement.forEach { (square, piece) -> squares[square.index] = piece }
            return Board(squares.toList())
        }
    }
}
