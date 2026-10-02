package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
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
 * A treatment of a whole square that marks a move in progress or just played (`M21.17`).
 *
 * Declared in drawing order, lowest first: the ordinary square, then the last move, then a
 * king in check, then the selected square. Each has a shape as well as a colour, so none is
 * told apart by colour alone, and a [description] for a screen reader.
 */
enum class SquareHighlight(
    val description: String,
) {
    /** Either square of the move just played: a tint and a triangle in each corner. */
    LAST_MOVE("last move"),

    /** The king of the side to move, while it is in check: a glow behind the king. */
    CHECK("king in check"),

    /** The piece being moved: a tint and an outline around the square. */
    SELECTED("selected"),
}

/**
 * Where the selected piece may go (`M21.17`), drawn above every [SquareHighlight] so it is
 * never hidden by one.
 */
enum class DestinationMark(
    val description: String,
) {
    /** An empty square the piece may move to: a dot. */
    MOVE("legal move"),

    /** A square whose piece may be captured: a ring around it. */
    CAPTURE("legal capture"),
}

/** Everything a square shows besides its shade and its piece, in drawing order (`M21.17`). */
data class SquareFeedback(
    /** The square treatments that apply, lowest first. */
    val highlights: List<SquareHighlight> = emptyList(),
    /** The destination mark on top of them, if the selected piece may go here. */
    val destination: DestinationMark? = null,
) {
    /** What a screen reader says about it, such as `"last move, legal capture"`, or `null` for nothing. */
    val description: String?
        get() = (highlights.map { it.description } + listOfNotNull(destination?.description)).joinToString(", ").ifEmpty { null }
}

/**
 * Turns a `chess-core` [Board] into the rows a chess board is drawn from.
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

    /**
     * Whether [piece] is drawn upside down, so it faces the player across the board.
     *
     * Only a [faceToFace] board does this — pass-and-play, where the second player sits
     * opposite (`D087`) — and only for the side at the top, the one [orientation] is not.
     */
    fun isUpsideDown(
        piece: Piece,
        orientation: Side,
        faceToFace: Boolean,
    ): Boolean = faceToFace && piece.side != orientation

    /**
     * The squares [game]'s latest move left and reached, for the last-move highlight, or none
     * before the first move.
     *
     * Read from `chess-core`'s active move history, so after an Undo it is the move that is
     * now latest. A castle is the king's two squares, as `chess-core` records it.
     */
    fun lastMoveSquares(game: ChessGame): Set<Square> = game.lastMove?.let { setOf(it.from, it.to) } ?: emptySet()

    /**
     * The square of the king that is in check in [game], or `null` when none is.
     *
     * Whether there is a check is `chess-core`'s answer (`ChessRules.isInCheck`); this only
     * finds the king it is about, which is the side to move's. A checkmated king stays marked.
     */
    fun checkedKing(game: ChessGame): Square? =
        if (ChessRules.isInCheck(game.state)) kingSquare(game.state.board, game.sideToMove) else null

    /** Where [side]'s king stands on [board], or `null` when it is not there. */
    fun kingSquare(
        board: Board,
        side: Side,
    ): Square? = board.squaresOf(side, PieceType.KING).firstOrNull()

    /**
     * What [square] shows (`M21.17`): which highlights apply, in the shared drawing order, and
     * the destination mark, a dot on an empty square or a ring around a piece to capture.
     */
    fun feedbackFor(
        square: BoardSquare,
        selected: Square?,
        legalDestinations: Set<Square>,
        lastMove: Set<Square>,
        checkedKing: Square?,
    ): SquareFeedback {
        val applies =
            mapOf(
                SquareHighlight.LAST_MOVE to (square.square in lastMove),
                SquareHighlight.CHECK to (square.square == checkedKing),
                SquareHighlight.SELECTED to (square.square == selected),
            )
        val destination =
            when {
                square.square !in legalDestinations -> null
                square.piece == null -> DestinationMark.MOVE
                else -> DestinationMark.CAPTURE
            }

        return SquareFeedback(highlights = SquareHighlight.entries.filter { applies.getValue(it) }, destination = destination)
    }

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

    /** A piece's name, `"Queen"`, for a screen reader and wherever a glyph alone is not enough. */
    fun nameFor(type: PieceType): String = type.name.lowercase().replaceFirstChar { it.uppercase() }

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
