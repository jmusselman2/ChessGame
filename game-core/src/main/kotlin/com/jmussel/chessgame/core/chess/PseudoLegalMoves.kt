package com.jmussel.chessgame.core.chess

/**
 * Movement geometry for a single piece, ignoring check.
 *
 * "Pseudo-legal" means the move respects how the piece moves, the edge of the board, and
 * the pieces in its way, but not whether it leaves its own king in check. Filtering those
 * out happens separately.
 */
object PseudoLegalMoves {
    /** The directions a sliding piece travels, or `null` when [type] does not slide. */
    fun slidingDirectionsFor(type: PieceType): List<Direction>? =
        when (type) {
            PieceType.ROOK -> Direction.ORTHOGONAL
            PieceType.BISHOP -> Direction.DIAGONAL
            PieceType.QUEEN -> Direction.ALL
            else -> null
        }

    /**
     * Squares the rook, bishop, or queen on [from] can reach: every empty square along each
     * of its directions, plus the first enemy piece it runs into. A friendly piece blocks
     * the ray without being a destination, and nothing beyond the first occupied square is
     * reachable.
     */
    fun slidingDestinations(
        board: Board,
        from: Square,
    ): List<Square> {
        val piece = requireNotNull(board.pieceAt(from)) { "No piece on $from" }
        val directions =
            requireNotNull(slidingDirectionsFor(piece.type)) {
                "${piece.type} is not a sliding piece"
            }
        return directions.flatMap { ray(board, from, it, piece.side) }
    }

    /** [slidingDestinations] expressed as moves from [from]. */
    fun slidingMoves(
        board: Board,
        from: Square,
    ): List<Move> = slidingDestinations(board, from).map { Move(from, it) }

    private fun ray(
        board: Board,
        from: Square,
        direction: Direction,
        side: Side,
    ): List<Square> =
        buildList {
            var square = from.shifted(direction)
            while (square != null) {
                val current = square
                val occupant = board.pieceAt(current)
                if (occupant != null) {
                    if (occupant.side != side) add(current)
                    break
                }
                add(current)
                square = current.shifted(direction)
            }
        }

    /** The eight knight steps: two squares one way and one the other. */
    val KNIGHT_STEPS: List<Direction> =
        listOf(
            Direction(1, 2),
            Direction(2, 1),
            Direction(2, -1),
            Direction(1, -2),
            Direction(-1, -2),
            Direction(-2, -1),
            Direction(-2, 1),
            Direction(-1, 2),
        )

    /**
     * Squares the knight on [from] can reach: any of its eight steps that stays on the
     * board and is either empty or holds an enemy piece. Knights jump, so pieces in
     * between are irrelevant.
     */
    fun knightDestinations(
        board: Board,
        from: Square,
    ): List<Square> {
        val piece = requireNotNull(board.pieceAt(from)) { "No piece on $from" }
        require(piece.type == PieceType.KNIGHT) { "${piece.type} is not a knight" }
        return KNIGHT_STEPS.mapNotNull { from.shifted(it) }.filter { board.pieceAt(it)?.side != piece.side }
    }

    /** [knightDestinations] expressed as moves from [from]. */
    fun knightMoves(
        board: Board,
        from: Square,
    ): List<Move> = knightDestinations(board, from).map { Move(from, it) }
}
