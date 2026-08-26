package com.jmussel.chessgame.core.chess

/**
 * Castling: the king's two-square move towards a rook, with the rook jumping to the far
 * side of it.
 *
 * A castling move is expressed as the king's move, for example `e1g1`. The rook's part is
 * applied by [LegalMoves.boardAfter].
 */
object Castling {
    /** The file a king stands on before castling. */
    const val KING_FILE: Int = 4

    /** The file a king ends on after castling. */
    fun kingDestinationFile(castlingSide: CastlingSide): Int = if (castlingSide == CastlingSide.KING_SIDE) 6 else 2

    /** The file the rook starts on. */
    fun rookOriginFile(castlingSide: CastlingSide): Int = if (castlingSide == CastlingSide.KING_SIDE) 7 else 0

    /** The file the rook ends on, which is also the square the king passes over. */
    fun rookDestinationFile(castlingSide: CastlingSide): Int = if (castlingSide == CastlingSide.KING_SIDE) 5 else 3

    fun kingOrigin(side: Side): Square = Square.of(KING_FILE, StandardPosition.backRankOf(side))

    fun kingDestination(
        side: Side,
        castlingSide: CastlingSide,
    ): Square = Square.of(kingDestinationFile(castlingSide), StandardPosition.backRankOf(side))

    fun rookOrigin(
        side: Side,
        castlingSide: CastlingSide,
    ): Square = Square.of(rookOriginFile(castlingSide), StandardPosition.backRankOf(side))

    fun rookDestination(
        side: Side,
        castlingSide: CastlingSide,
    ): Square = Square.of(rookDestinationFile(castlingSide), StandardPosition.backRankOf(side))

    /** The squares that must be empty between king and rook. */
    fun pathSquares(
        side: Side,
        castlingSide: CastlingSide,
    ): List<Square> {
        val rank = StandardPosition.backRankOf(side)
        val files = if (castlingSide == CastlingSide.KING_SIDE) listOf(5, 6) else listOf(1, 2, 3)
        return files.map { Square.of(it, rank) }
    }

    /**
     * The squares the king occupies, crosses, and lands on. None of them may be attacked.
     */
    fun kingWalkSquares(
        side: Side,
        castlingSide: CastlingSide,
    ): List<Square> =
        listOf(
            kingOrigin(side),
            rookDestination(side, castlingSide),
            kingDestination(side, castlingSide),
        )

    /**
     * Whether [move] is a castling move on [board]: a king moving two files along its rank.
     */
    fun isCastlingMove(
        board: Board,
        move: Move,
    ): Boolean {
        val piece = board.pieceAt(move.from) ?: return false
        return piece.type == PieceType.KING &&
            move.from.rank == move.to.rank &&
            kotlin.math.abs(move.to.file - move.from.file) == 2
    }

    /** Which side of the board [move] castles towards. */
    fun castlingSideOf(move: Move): CastlingSide = if (move.to.file > move.from.file) CastlingSide.KING_SIDE else CastlingSide.QUEEN_SIDE

    /**
     * Whether [side] may castle [castlingSide] in [state]: the right is still held, the
     * king and rook stand on their home squares, the squares between them are empty, the
     * king is not in check, and neither the square it crosses nor its destination is
     * attacked.
     */
    fun canCastle(
        state: GameState,
        side: Side,
        castlingSide: CastlingSide,
    ): Boolean {
        if (!state.castlingRights.has(side, castlingSide)) return false

        val board = state.board
        if (board.pieceAt(kingOrigin(side)) != Piece(side, PieceType.KING)) return false
        if (board.pieceAt(rookOrigin(side, castlingSide)) != Piece(side, PieceType.ROOK)) return false
        if (pathSquares(side, castlingSide).any { !board.isEmpty(it) }) return false

        return kingWalkSquares(side, castlingSide).none { Attacks.isAttacked(board, it, side.opposite) }
    }

    /** The castling moves available to the side to move in [state]. */
    fun availableMoves(state: GameState): List<Move> {
        val side = state.sideToMove
        if (!state.castlingRights.hasAny(side)) return emptyList()
        return CastlingSide.entries
            .filter { canCastle(state, side, it) }
            .map { Move(kingOrigin(side), kingDestination(side, it)) }
    }
}
