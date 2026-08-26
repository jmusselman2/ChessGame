package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
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
     * across, with [orientation]'s own side at the bottom.
     *
     * Viewed from White that is rank 8 first and file `a` on the left; viewed from Black
     * both are reversed, so each player sees their own pieces nearest to them.
     */
    fun rows(
        board: Board,
        orientation: Side = Side.WHITE,
    ): List<List<BoardSquare>> {
        val ranks = (0 until Square.RANKS).sortedByDescending { if (orientation == Side.WHITE) it else -it }
        val files = (0 until Square.FILES).sortedBy { if (orientation == Side.WHITE) it else -it }
        return ranks.map { rank -> files.map { file -> squareAt(board, Square.of(file, rank)) } }
    }

    /** Every square in drawing order for [orientation]. */
    fun squares(
        board: Board,
        orientation: Side = Side.WHITE,
    ): List<BoardSquare> = rows(board, orientation).flatten()

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

    /** The file letters, left to right, as [orientation] sees them. */
    fun fileLabels(orientation: Side = Side.WHITE): List<String> {
        val labels = (0 until Square.FILES).map { Square.of(it, 0).fileChar.toString() }
        return if (orientation == Side.WHITE) labels else labels.reversed()
    }

    /** The rank numbers, top to bottom, as [orientation] sees them. */
    fun rankLabels(orientation: Side = Side.WHITE): List<String> {
        val labels = (Square.RANKS downTo 1).map { it.toString() }
        return if (orientation == Side.WHITE) labels else labels.reversed()
    }

    private fun squareAt(
        board: Board,
        square: Square,
    ): BoardSquare = BoardSquare(square = square, piece = board.pieceAt(square), isLight = isLight(square))
}
