package com.jmussel.chessgame.core.chess

/**
 * Legality on top of movement geometry: a move is illegal if it leaves its own king in
 * check.
 *
 * This covers moving a pinned piece off its pin line, stepping a king onto an attacked
 * square, and failing to answer an existing check. Castling, en passant, and promotion
 * are separate rules and are not generated here yet.
 */
object LegalMoves {
    /**
     * The board after [move] is played: the piece leaves [Move.from], captures whatever
     * stands on [Move.to], and becomes [Move.promotion] when one is given. When the move
     * is a castling king move, the rook jumps to the far side of the king with it.
     *
     * The en passant capture and every non-board part of the state are handled by their
     * own rules.
     */
    fun boardAfter(
        board: Board,
        move: Move,
    ): Board {
        val piece = requireNotNull(board.pieceAt(move.from)) { "No piece on ${move.from}" }
        val moved = if (move.promotion == null) piece else piece.copy(type = move.promotion)
        val relocated = board.withoutPiece(move.from).withPiece(move.to, moved)

        if (!Castling.isCastlingMove(board, move)) return relocated

        val castlingSide = Castling.castlingSideOf(move)
        val rookFrom = Castling.rookOrigin(piece.side, castlingSide)
        val rook = requireNotNull(board.pieceAt(rookFrom)) { "No rook on $rookFrom to castle with" }
        return relocated
            .withoutPiece(rookFrom)
            .withPiece(Castling.rookDestination(piece.side, castlingSide), rook)
    }

    /** Whether playing [move] would leave the moving side's own king in check. */
    fun leavesOwnKingInCheck(
        board: Board,
        move: Move,
    ): Boolean {
        val piece = requireNotNull(board.pieceAt(move.from)) { "No piece on ${move.from}" }
        return Attacks.isInCheck(boardAfter(board, move), piece.side)
    }

    /** Whether [move] is both pseudo-legal for the piece on [Move.from] and not self-check. */
    fun isLegal(
        board: Board,
        move: Move,
    ): Boolean {
        if (board.pieceAt(move.from) == null) return false
        return move in PseudoLegalMoves.from(board, move.from) && !leavesOwnKingInCheck(board, move)
    }

    /** Every legal move for the piece on [from]. */
    fun from(
        board: Board,
        from: Square,
    ): List<Move> = PseudoLegalMoves.from(board, from).filterNot { leavesOwnKingInCheck(board, it) }

    /** Every legal move for [side], in square order. */
    fun forSide(
        board: Board,
        side: Side,
    ): List<Move> = PseudoLegalMoves.forSide(board, side).filterNot { leavesOwnKingInCheck(board, it) }

    /**
     * Every legal move for the side to move in [state], including castling.
     *
     * Castling needs the castling rights that only [GameState] carries, so this is the
     * entry point that produces a complete move list.
     */
    fun forSideToMove(state: GameState): List<Move> = forSide(state.board, state.sideToMove) + Castling.availableMoves(state)

    /** Whether [move] is legal for the side to move in [state]. */
    fun isLegal(
        state: GameState,
        move: Move,
    ): Boolean = move in forSideToMove(state)
}
