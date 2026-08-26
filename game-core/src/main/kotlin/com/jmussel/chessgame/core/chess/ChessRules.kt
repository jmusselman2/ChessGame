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

    /** Whether the side to move is in check. */
    fun isInCheck(state: GameState): Boolean = Attacks.isSideToMoveInCheck(state)

    /** Whether the side to move has no legal move at all. */
    fun hasNoLegalMoves(state: GameState): Boolean = legalMoves(state).isEmpty()

    /** Whether the side to move is checkmated: in check with no legal move. */
    fun isCheckmate(state: GameState): Boolean = isInCheck(state) && hasNoLegalMoves(state)

    /** Whether the side to move is stalemated: not in check, but with no legal move. */
    fun isStalemate(state: GameState): Boolean = !isInCheck(state) && hasNoLegalMoves(state)

    /**
     * The result [state] has already reached on its own, or `null` when play continues.
     *
     * Only conditions that end the game automatically appear here. Claimable draws
     * (threefold repetition, the fifty-move rule) and resignation are separate actions.
     *
     * Insufficient material is checked before stalemate: a dead position ends the game the
     * moment the material becomes insufficient, which is before any later stalemate could
     * arise. Both are draws either way.
     */
    fun terminalResult(state: GameState): GameResult? {
        state.result?.let { return it }

        return when {
            isCheckmate(state) -> GameResult.checkmate(loser = state.sideToMove)
            InsufficientMaterial.isDraw(state) -> GameResult.draw(TerminationReason.INSUFFICIENT_MATERIAL)
            Repetition.isFivefold(state) -> GameResult.draw(TerminationReason.FIVEFOLD_REPETITION)
            MoveCountDraws.isSeventyFiveMoveDraw(state) -> GameResult.draw(TerminationReason.SEVENTY_FIVE_MOVE_RULE)
            isStalemate(state) -> GameResult.draw(TerminationReason.STALEMATE)
            else -> null
        }
    }

    /**
     * The state after [move] is played. [move] must be legal in [state].
     *
     * Updates the board, the side to move, castling rights, the en passant target, the
     * halfmove clock, the fullmove number, and the repetition count of the new position,
     * and records a terminal result when the move ends the game.
     *
     * A pawn move or capture is irreversible: it resets the halfmove clock and clears the
     * repetition history, because no earlier position can occur again.
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

        val drawRuleState =
            if (resetsClock) {
                DrawRuleState()
            } else {
                state.drawRuleState.withHalfmoveClock(state.drawRuleState.halfmoveClock + 1)
            }

        val next =
            Repetition.recording(
                state.copy(
                    board = LegalMoves.boardAfter(state, move),
                    sideToMove = state.sideToMove.opposite,
                    castlingRights = castlingRightsAfter(state, move, piece),
                    enPassantTarget = EnPassant.targetAfter(state.board, move),
                    drawRuleState = drawRuleState,
                    fullmoveNumber =
                        if (state.sideToMove == Side.BLACK) state.fullmoveNumber + 1 else state.fullmoveNumber,
                ),
            )

        return next.copy(result = terminalResult(next))
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
