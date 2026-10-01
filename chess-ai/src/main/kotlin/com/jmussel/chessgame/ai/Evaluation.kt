package com.jmussel.chessgame.ai

import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import kotlin.math.abs
import kotlin.math.max

/**
 * How good a position looks, in centipawns, from the side to move's point of view: the
 * material on the board plus simple positional terms (`D086`).
 *
 * Deliberately simple. Pawns gain for advancing and for holding the centre, minor pieces
 * and the queen for being central, rooks for reaching the seventh rank, and the king for
 * staying home behind its pawns until the endgame, when it should come to the centre.
 */
internal object Evaluation {
    const val PAWN = 100
    const val KNIGHT = 320
    const val BISHOP = 330
    const val ROOK = 500
    const val QUEEN = 900

    /** Material other than pawns at or below which a position counts as an endgame. */
    private const val ENDGAME_MATERIAL = 2 * ROOK + 2 * BISHOP

    fun valueOf(type: PieceType): Int =
        when (type) {
            PieceType.PAWN -> PAWN
            PieceType.KNIGHT -> KNIGHT
            PieceType.BISHOP -> BISHOP
            PieceType.ROOK -> ROOK
            PieceType.QUEEN -> QUEEN
            PieceType.KING -> 0
        }

    fun evaluate(state: GameState): Int {
        val pieces = state.board.occupiedSquares()
        val nonPawnMaterial = pieces.sumOf { (_, piece) -> if (piece.type == PieceType.PAWN) 0 else valueOf(piece.type) }
        val endgame = nonPawnMaterial <= ENDGAME_MATERIAL

        var white = 0
        pieces.forEach { (square, piece) ->
            val score = valueOf(piece.type) + positional(piece.type, piece.side, square, endgame)
            white += if (piece.side == Side.WHITE) score else -score
        }
        return if (state.sideToMove == Side.WHITE) white else -white
    }

    private fun positional(
        type: PieceType,
        side: Side,
        square: Square,
        endgame: Boolean,
    ): Int {
        // Ranks counted from the piece's own side: 0 is its back rank.
        val rank = if (side == Side.WHITE) square.rank else Square.RANKS - 1 - square.rank
        val file = square.file
        val fromCentre = max(abs(2 * file - 7), abs(2 * square.rank - 7)) / 2 // 0 in the middle four, 3 on the edge

        return when (type) {
            PieceType.PAWN -> (rank - 1) * 8 + if ((file == 3 || file == 4) && rank >= 3) 15 else 0
            PieceType.KNIGHT -> 20 - 12 * fromCentre
            PieceType.BISHOP -> 10 - 5 * fromCentre
            PieceType.ROOK -> if (rank == 6) 15 else 0
            PieceType.QUEEN -> 5 - 3 * fromCentre
            PieceType.KING ->
                when {
                    endgame -> 15 - 10 * fromCentre
                    rank == 0 && file in SHELTERED_FILES -> 20
                    rank == 0 -> 0
                    else -> -15 * rank
                }
        }
    }

    private val SHELTERED_FILES = setOf(0, 1, 2, 6, 7)
}
