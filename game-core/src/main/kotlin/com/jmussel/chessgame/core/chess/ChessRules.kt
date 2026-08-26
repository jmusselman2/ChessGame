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
     * The draws the side to move may claim right now.
     *
     * A claimable draw does not end the game on its own; it stays available until it is
     * claimed or the position changes (`D019`).
     */
    fun availableDrawClaims(state: GameState): Set<DrawClaim> =
        buildSet {
            if (Repetition.canClaimThreefold(state)) add(DrawClaim.THREEFOLD_REPETITION)
            if (MoveCountDraws.canClaimFiftyMove(state)) add(DrawClaim.FIFTY_MOVE_RULE)
        }

    /** Whether [claim] is currently valid. */
    fun canClaimDraw(
        state: GameState,
        claim: DrawClaim,
    ): Boolean = claim in availableDrawClaims(state)

    /**
     * The state after a valid [claim] is made: a finished game drawn for that reason.
     *
     * [claim] must be valid in [state]; check with [canClaimDraw] or
     * [availableDrawClaims] first.
     */
    fun claimDraw(
        state: GameState,
        claim: DrawClaim,
    ): GameState {
        require(!state.isOver) { "The game is over: ${state.result}" }
        require(canClaimDraw(state, claim)) { "No valid $claim claim is available" }

        val reason =
            when (claim) {
                DrawClaim.THREEFOLD_REPETITION -> TerminationReason.THREEFOLD_REPETITION_CLAIM
                DrawClaim.FIFTY_MOVE_RULE -> TerminationReason.FIFTY_MOVE_RULE_CLAIM
            }
        return state.copy(result = GameResult.draw(reason))
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

    /** Every legal move for the side to move in [game]. */
    fun legalMoves(game: ChessGame): List<Move> = legalMoves(game.state)

    /** Whether [move] is legal in [game]. */
    fun isLegal(
        game: ChessGame,
        move: Move,
    ): Boolean = isLegal(game.state, move)

    /** The draws the side to move may claim in [game]. */
    fun availableDrawClaims(game: ChessGame): Set<DrawClaim> = availableDrawClaims(game.state)

    /** [game] after [move] is played, with the prior position recorded in the history. */
    fun applyMove(
        game: ChessGame,
        move: Move,
    ): ChessGame =
        ChessGame(
            state = applyMove(game.state, move),
            history = game.history + MoveRecord(move, game.state),
        )

    /** [game] drawn by a valid [claim]. The move history is unaffected. */
    fun claimDraw(
        game: ChessGame,
        claim: DrawClaim,
    ): ChessGame = game.copy(state = claimDraw(game.state, claim))

    /**
     * [game] with its most recent move taken back, restoring the position exactly as it
     * was before that move.
     *
     * This is the mechanical restoration. Whether the move may be taken back at all is
     * [canUndo].
     */
    fun undoLastMove(game: ChessGame): ChessGame {
        val last = requireNotNull(game.history.lastOrNull()) { "There is no move to undo" }
        return ChessGame(state = last.positionBefore, history = game.history.dropLast(1))
    }

    /**
     * The side that may currently take a move back, or `null` when nobody may.
     *
     * Only the latest move can be undone, and only by the player who made it. That move is
     * unanswered by definition — as soon as the opponent replies, their reply becomes the
     * latest move and the earlier one is locked. If the opponent then takes their reply
     * back, the previous move becomes undoable again (`D016`).
     *
     * A finished game has nothing to take back: a move that ends the game is final the
     * moment it is made, with no grace period (`D017`), and so is a claimed draw or a
     * resignation (`D018`).
     */
    fun undoableSide(game: ChessGame): Side? = if (game.isOver) null else game.lastMover

    /** Whether [side] may take its own latest unanswered move back. */
    fun canUndo(
        game: ChessGame,
        side: Side,
    ): Boolean = undoableSide(game) == side

    /**
     * [game] with [side]'s latest unanswered move taken back.
     *
     * [side] must be allowed to undo; check with [canUndo] first.
     */
    fun undo(
        game: ChessGame,
        side: Side,
    ): ChessGame {
        require(canUndo(game, side)) { "$side has no move to take back" }
        return undoLastMove(game)
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
