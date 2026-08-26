package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Square

/** One square as the board is drawn: where it is, what stands on it, and its shade. */
data class BoardSquare(
    val square: Square,
    val piece: Piece?,
    val isLight: Boolean,
)

/**
 * Turns a `game-core` [Board] into the rows a chess board is drawn from.
 *
 * This holds no chess rules — it only decides what the board looks like, so the Compose
 * layer stays a thin renderer over it.
 */
object BoardRendering {
    /**
     * The board as eight rows, drawn from the top of the screen down and from the left
     * across: rank 8 first, file `a` first.
     */
    fun rows(board: Board): List<List<BoardSquare>> =
        (Square.RANKS - 1 downTo 0).map { rank ->
            (0 until Square.FILES).map { file -> squareAt(board, Square.of(file, rank)) }
        }

    /** Every square in drawing order, rank 8 down to rank 1. */
    fun squares(board: Board): List<BoardSquare> = rows(board).flatten()

    /** Whether [square] is one of the light squares. `a1` is dark. */
    fun isLight(square: Square): Boolean = (square.file + square.rank) % 2 == 1

    /**
     * The chess glyph for [type].
     *
     * The solid glyphs are used for both sides, and colour tells them apart — the outline
     * glyphs are nearly invisible on a light square.
     */
    fun glyphFor(type: PieceType): Char =
        when (type) {
            PieceType.KING -> '♚'
            PieceType.QUEEN -> '♛'
            PieceType.ROOK -> '♜'
            PieceType.BISHOP -> '♝'
            PieceType.KNIGHT -> '♞'
            PieceType.PAWN -> '♟'
        }

    /** The file letters, left to right. */
    val fileLabels: List<String> = (0 until Square.FILES).map { Square.of(it, 0).fileChar.toString() }

    /** The rank numbers, as drawn from top to bottom. */
    val rankLabels: List<String> = (Square.RANKS downTo 1).map { it.toString() }

    private fun squareAt(
        board: Board,
        square: Square,
    ): BoardSquare = BoardSquare(square = square, piece = board.pieceAt(square), isLight = isLight(square))
}
