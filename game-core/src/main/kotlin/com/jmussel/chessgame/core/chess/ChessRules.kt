package com.jmussel.chessgame.core.chess

/**
 * The chess engine's entry point: which moves are legal in a position, and what the
 * position becomes after one is played.
 */
object ChessRules {
    /** Every legal move for the side to move. */
    fun legalMoves(state: GameState): List<Move> = LegalMoves.forSideToMove(state)

    /** Whether [move] is legal for the side to move. */
    fun isLegal(
        state: GameState,
        move: Move,
    ): Boolean = LegalMoves.isLegal(state, move)

    /**
     * The state after [move] is played. [move] must be legal in [state].
     *
     * Updates the board, the side to move, castling rights, the en passant target, the
     * halfmove clock, and the fullmove number. Repetition tracking and terminal results
     * are decided by their own rules and are not applied here.
     */
    fun applyMove(
        state: GameState,
        move: Move,
    ): GameState {
        require(!state.isOver) { "The game is over: ${state.result}" }
        require(isLegal(state, move)) { "Illegal move $move" }

        val piece = requireNotNull(state.board.pieceAt(move.from)) { "No piece on ${move.from}" }
        val isCapture = !state.board.isEmpty(move.to) || EnPassant.isCapture(state, move)
        val resetsClock = piece.type == PieceType.PAWN || isCapture

        return state.copy(
            board = LegalMoves.boardAfter(state, move),
            sideToMove = state.sideToMove.opposite,
            castlingRights = castlingRightsAfter(state, move, piece),
            enPassantTarget = EnPassant.targetAfter(state.board, move),
            drawRuleState =
                state.drawRuleState.withHalfmoveClock(
                    if (resetsClock) 0 else state.drawRuleState.halfmoveClock + 1,
                ),
            fullmoveNumber =
                if (state.sideToMove == Side.BLACK) state.fullmoveNumber + 1 else state.fullmoveNumber,
        )
    }

    /**
     * Castling rights after [move]: a side loses both rights when its king moves, loses
     * one when that rook leaves its home square, and its opponent loses one when that
     * rook is captured on its home square.
     */
    private fun castlingRightsAfter(
        state: GameState,
        move: Move,
        piece: Piece,
    ): CastlingRights {
        var rights = state.castlingRights

        if (piece.type == PieceType.KING) {
            rights = rights.without(piece.side)
        }

        CastlingSide.entries.forEach { castlingSide ->
            if (move.from == Castling.rookOrigin(piece.side, castlingSide)) {
                rights = rights.without(piece.side, castlingSide)
            }
            if (move.to == Castling.rookOrigin(piece.side.opposite, castlingSide)) {
                rights = rights.without(piece.side.opposite, castlingSide)
            }
        }

        return rights
    }
}
