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
        return directions.flatMap { ray(board, from, it) }.filter { board.pieceAt(it)?.side != piece.side }
    }

    /** [slidingDestinations] expressed as moves from [from]. */
    fun slidingMoves(
        board: Board,
        from: Square,
    ): List<Move> = slidingDestinations(board, from).map { Move(from, it) }

    /**
     * Every square along [direction] from [from] up to and including the first occupied
     * one, whichever side occupies it. Callers decide whether that blocker is a legal
     * destination or merely a defended square.
     */
    internal fun ray(
        board: Board,
        from: Square,
        direction: Direction,
    ): List<Square> =
        buildList {
            var square = from.shifted(direction)
            while (square != null) {
                val current = square
                add(current)
                if (!board.isEmpty(current)) break
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

    /**
     * Squares the king on [from] can step to: any of the eight adjacent squares that stays
     * on the board and is not occupied by a friendly piece.
     *
     * This is plain king movement only. Castling is a separate rule, and whether a
     * destination is attacked is not considered here.
     */
    fun kingDestinations(
        board: Board,
        from: Square,
    ): List<Square> {
        val piece = requireNotNull(board.pieceAt(from)) { "No piece on $from" }
        require(piece.type == PieceType.KING) { "${piece.type} is not a king" }
        return Direction.ALL.mapNotNull { from.shifted(it) }.filter { board.pieceAt(it)?.side != piece.side }
    }

    /** [kingDestinations] expressed as moves from [from]. */
    fun kingMoves(
        board: Board,
        from: Square,
    ): List<Move> = kingDestinations(board, from).map { Move(from, it) }

    /** The direction a [side]'s pawns advance: north for White, south for Black. */
    fun pawnAdvanceDirection(side: Side): Direction = if (side == Side.WHITE) Direction.NORTH else Direction.SOUTH

    /**
     * The two squares diagonally ahead of a [side] pawn on [from], regardless of what
     * stands there. These are the squares such a pawn attacks.
     */
    fun pawnCaptureSquares(
        from: Square,
        side: Side,
    ): List<Square> {
        val forward = pawnAdvanceDirection(side).rankStep
        return listOfNotNull(
            from.shifted(Direction(-1, forward)),
            from.shifted(Direction(1, forward)),
        )
    }

    /**
     * Squares the pawn on [from] can move to: one square forward when empty, two squares
     * forward from its starting rank when both are empty, and either diagonal forward
     * square that holds an enemy piece.
     *
     * En passant and promotion are separate rules and are not applied here.
     */
    fun pawnDestinations(
        board: Board,
        from: Square,
    ): List<Square> {
        val piece = requireNotNull(board.pieceAt(from)) { "No piece on $from" }
        require(piece.type == PieceType.PAWN) { "${piece.type} is not a pawn" }
        val direction = pawnAdvanceDirection(piece.side)

        val advances =
            buildList {
                val oneAhead = from.shifted(direction) ?: return@buildList
                if (!board.isEmpty(oneAhead)) return@buildList
                add(oneAhead)

                if (from.rank != StandardPosition.pawnRankOf(piece.side)) return@buildList
                val twoAhead = oneAhead.shifted(direction) ?: return@buildList
                if (board.isEmpty(twoAhead)) add(twoAhead)
            }

        val captures =
            pawnCaptureSquares(from, piece.side).filter {
                board.pieceAt(it)?.side == piece.side.opposite
            }

        return advances + captures
    }

    /** The rank a [side] pawn promotes on: rank 8 for White, rank 1 for Black. */
    fun promotionRankOf(side: Side): Int = StandardPosition.backRankOf(side.opposite)

    /**
     * [pawnDestinations] expressed as moves from [from].
     *
     * A destination on the promotion rank produces one move per choice in
     * [PieceType.PROMOTION_CHOICES] — the player always chooses, so a bare move onto that
     * rank is not generated and is therefore never legal.
     */
    fun pawnMoves(
        board: Board,
        from: Square,
    ): List<Move> {
        val piece = requireNotNull(board.pieceAt(from)) { "No piece on $from" }
        val promotionRank = promotionRankOf(piece.side)
        return pawnDestinations(board, from).flatMap { to ->
            if (to.rank == promotionRank) {
                PieceType.PROMOTION_CHOICES.map { Move(from, to, it) }
            } else {
                listOf(Move(from, to))
            }
        }
    }

    /**
     * Every pseudo-legal move for the piece on [from], dispatched by piece type.
     *
     * Castling, en passant, and promotion are separate rules and are not included here.
     */
    fun from(
        board: Board,
        from: Square,
    ): List<Move> {
        val piece = requireNotNull(board.pieceAt(from)) { "No piece on $from" }
        return when (piece.type) {
            PieceType.PAWN -> pawnMoves(board, from)
            PieceType.KNIGHT -> knightMoves(board, from)
            PieceType.KING -> kingMoves(board, from)
            else -> slidingMoves(board, from)
        }
    }

    /** Every pseudo-legal move for [side], in square order. */
    fun forSide(
        board: Board,
        side: Side,
    ): List<Move> = board.squaresOf(side).flatMap { from(board, it) }
}
