package com.jmussel.chessgame.core.chess

/**
 * Standard algebraic notation (SAN) for a legal move, as [ChessRules.san] writes it (`M22.6`).
 *
 * Display only: a move's identity is its `from`, `to` and promotion, and notation is never
 * stored or sent as canonical data (`D101`). Everything here is worked out from the position
 * the move is played from.
 */
internal object Notation {
    fun san(
        state: GameState,
        move: Move,
    ): String {
        val piece = requireNotNull(state.board.pieceAt(move.from)) { "No piece on ${move.from}" }
        val body =
            when {
                piece.type == PieceType.KING && move.to.file - move.from.file == 2 -> KING_SIDE
                piece.type == PieceType.KING && move.from.file - move.to.file == 2 -> QUEEN_SIDE
                piece.type == PieceType.PAWN -> pawnMove(move)
                else -> pieceMove(state, move, piece)
            }

        return body + checkSuffix(ChessRules.applyMove(state, move))
    }

    /** `e4`, `exd5`, `exd6` for en passant, `e8=Q`, `dxe8=N`. */
    private fun pawnMove(move: Move): String {
        val capture = move.from.file != move.to.file
        val promotion = move.promotion?.let { "=${it.letter}" }.orEmpty()
        return (if (capture) "${move.from.fileChar}$CAPTURE" else "") + move.to.name + promotion
    }

    /** `Nf3`, `Bxc6`, with only as much of the starting square as tells it apart: `Nbd2`, `R1e2`, `Qh4e1`. */
    private fun pieceMove(
        state: GameState,
        move: Move,
        piece: Piece,
    ): String {
        val rivals =
            ChessRules
                .legalMoves(state)
                .filter { it.to == move.to && it.from != move.from && state.board.pieceAt(it.from) == piece }
                .map { it.from }
                .distinct()
        val from =
            when {
                rivals.isEmpty() -> ""
                rivals.none { it.file == move.from.file } -> move.from.fileChar.toString()
                rivals.none { it.rank == move.from.rank } -> move.from.rankNumber.toString()
                else -> move.from.name
            }
        val capture = if (state.board.pieceAt(move.to) != null) CAPTURE else ""

        return "${piece.type.letter}$from$capture${move.to.name}"
    }

    /** `#` when the move mates, `+` when it checks, nothing otherwise. */
    private fun checkSuffix(after: GameState): String =
        when {
            ChessRules.isCheckmate(after) -> MATE
            ChessRules.isInCheck(after) -> CHECK
            else -> ""
        }

    private const val KING_SIDE = "O-O"
    private const val QUEEN_SIDE = "O-O-O"
    private const val CAPTURE = "x"
    private const val CHECK = "+"
    private const val MATE = "#"
}
