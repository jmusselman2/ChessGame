package com.jmussel.chessgame.core.chess

/**
 * A requested move: the piece on [from] moves to [to].
 *
 * [promotion] is set only for a pawn move reaching the far rank, and must be one of
 * [PieceType.PROMOTION_CHOICES]. Castling is expressed as the king's two-square move, and
 * en passant as the capturing pawn's diagonal move; neither needs its own field because
 * both are derivable from the position the move is applied to.
 */
data class Move(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null,
) {
    init {
        require(from != to) { "A move must change square: $from" }
        require(promotion == null || promotion in PieceType.PROMOTION_CHOICES) {
            "Invalid promotion piece: $promotion"
        }
    }

    override fun toString(): String = "$from$to${promotion?.letter?.lowercaseChar() ?: ""}"

    companion object {
        /** Builds a move from algebraic square names, for example `of("e2", "e4")`. */
        fun of(
            from: String,
            to: String,
            promotion: PieceType? = null,
        ): Move = Move(Square.parse(from), Square.parse(to), promotion)
    }
}
