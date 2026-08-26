package com.jmussel.chessgame.core.chess

/**
 * The standard chess starting position.
 */
object StandardPosition {
    /** Back-rank piece types from the a file to the h file. */
    val BACK_RANK: List<PieceType> =
        listOf(
            PieceType.ROOK,
            PieceType.KNIGHT,
            PieceType.BISHOP,
            PieceType.QUEEN,
            PieceType.KING,
            PieceType.BISHOP,
            PieceType.KNIGHT,
            PieceType.ROOK,
        )

    /** The rank a side's back-rank pieces start on: `0` (rank 1) for White, `7` (rank 8) for Black. */
    fun backRankOf(side: Side): Int = if (side == Side.WHITE) 0 else Square.RANKS - 1

    /** The rank a side's pawns start on: `1` (rank 2) for White, `6` (rank 7) for Black. */
    fun pawnRankOf(side: Side): Int = if (side == Side.WHITE) 1 else Square.RANKS - 2

    /** Piece placement for a new game: 32 pieces on ranks 1, 2, 7, and 8. */
    val BOARD: Board =
        Board.of(
            buildMap {
                Side.entries.forEach { side ->
                    (0 until Square.FILES).forEach { file ->
                        put(Square.of(file, backRankOf(side)), Piece(side, BACK_RANK[file]))
                        put(Square.of(file, pawnRankOf(side)), Piece(side, PieceType.PAWN))
                    }
                }
            },
        )

    /**
     * A new game: the standard board, White to move, all castling rights, no en passant
     * target, no draw-rule history, move 1, and no result.
     */
    fun newGame(): GameState =
        Repetition.recording(
            GameState(
                board = BOARD,
                sideToMove = Side.WHITE,
                castlingRights = CastlingRights.ALL,
                enPassantTarget = null,
                drawRuleState = DrawRuleState(),
                fullmoveNumber = 1,
                result = null,
            ),
        )
}
