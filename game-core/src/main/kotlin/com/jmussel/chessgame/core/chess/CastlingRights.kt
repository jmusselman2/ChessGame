package com.jmussel.chessgame.core.chess

/** The two sides of the board a king may castle towards. */
enum class CastlingSide {
    KING_SIDE,
    QUEEN_SIDE,
}

/**
 * Which castlings remain permitted, ignoring whether they are legal right now.
 *
 * A right is lost permanently once the king or the relevant rook moves; whether the king
 * is currently in check, or the path is clear, is evaluated per move and is not stored here.
 */
data class CastlingRights(
    val whiteKingSide: Boolean,
    val whiteQueenSide: Boolean,
    val blackKingSide: Boolean,
    val blackQueenSide: Boolean,
) {
    fun has(
        side: Side,
        castlingSide: CastlingSide,
    ): Boolean =
        when (side) {
            Side.WHITE -> if (castlingSide == CastlingSide.KING_SIDE) whiteKingSide else whiteQueenSide
            Side.BLACK -> if (castlingSide == CastlingSide.KING_SIDE) blackKingSide else blackQueenSide
        }

    fun hasAny(side: Side): Boolean = has(side, CastlingSide.KING_SIDE) || has(side, CastlingSide.QUEEN_SIDE)

    /** Returns a copy with one specific right removed. */
    fun without(
        side: Side,
        castlingSide: CastlingSide,
    ): CastlingRights =
        when {
            side == Side.WHITE && castlingSide == CastlingSide.KING_SIDE -> copy(whiteKingSide = false)
            side == Side.WHITE -> copy(whiteQueenSide = false)
            castlingSide == CastlingSide.KING_SIDE -> copy(blackKingSide = false)
            else -> copy(blackQueenSide = false)
        }

    /** Returns a copy with both of [side]'s rights removed. */
    fun without(side: Side): CastlingRights =
        when (side) {
            Side.WHITE -> copy(whiteKingSide = false, whiteQueenSide = false)
            Side.BLACK -> copy(blackKingSide = false, blackQueenSide = false)
        }

    /** FEN-style rendering: `KQkq`, or `-` when no rights remain. */
    override fun toString(): String {
        val text =
            buildString {
                if (whiteKingSide) append('K')
                if (whiteQueenSide) append('Q')
                if (blackKingSide) append('k')
                if (blackQueenSide) append('q')
            }
        return text.ifEmpty { "-" }
    }

    companion object {
        val ALL = CastlingRights(whiteKingSide = true, whiteQueenSide = true, blackKingSide = true, blackQueenSide = true)
        val NONE = CastlingRights(whiteKingSide = false, whiteQueenSide = false, blackKingSide = false, blackQueenSide = false)
    }
}
