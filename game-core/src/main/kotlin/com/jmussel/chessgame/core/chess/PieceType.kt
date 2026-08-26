package com.jmussel.chessgame.core.chess

/**
 * The six standard chess piece types.
 *
 * [letter] is the uppercase English algebraic-notation letter for the type.
 */
enum class PieceType(
    val letter: Char,
) {
    PAWN('P'),
    KNIGHT('N'),
    BISHOP('B'),
    ROOK('R'),
    QUEEN('Q'),
    KING('K'),
    ;

    companion object {
        /**
         * The piece types a pawn may promote to, in conventional preference order.
         *
         * Promotion is always an explicit choice; there is no automatic queen promotion.
         */
        val PROMOTION_CHOICES: List<PieceType> = listOf(QUEEN, ROOK, BISHOP, KNIGHT)

        fun fromLetter(letter: Char): PieceType =
            entries.firstOrNull { it.letter == letter.uppercaseChar() }
                ?: throw IllegalArgumentException("Unknown piece letter: $letter")
    }
}
