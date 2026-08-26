package com.jmussel.chessgame.core.chess

/**
 * A single chess piece: a [type] belonging to a [side].
 *
 * A piece has no square of its own; placement is owned by [Board].
 */
data class Piece(
    val side: Side,
    val type: PieceType,
) {
    /**
     * Standard FEN-style symbol: uppercase for White, lowercase for Black.
     */
    val symbol: Char
        get() = if (side == Side.WHITE) type.letter else type.letter.lowercaseChar()

    override fun toString(): String = symbol.toString()

    companion object {
        fun fromSymbol(symbol: Char): Piece {
            val side = if (symbol.isUpperCase()) Side.WHITE else Side.BLACK
            return Piece(side, PieceType.fromLetter(symbol))
        }
    }
}
