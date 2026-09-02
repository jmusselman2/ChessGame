package com.jmussel.chessgame.core.chess

/**
 * The chess engine's entry point: which moves are legal in a position, and what the
 * position becomes after one is played.
 */
object ChessRules {
    /**
     * Every legal move for the side to move, or nothing once the game is over.
     *
     * A finished game admits no move: a terminal result is final the moment it is recorded,
     * with no pending-final state (`D017`), and [applyMove] rejects any move in that state.
     * This query and that transition must not disagree.
     */
    fun legalMoves(state: GameState): List<Move> = if (state.isOver) emptyList() else LegalMoves.forSideToMove(state)

    /**
     * Whether [move] is legal for the side to move. Always `false` once the game is over,
     * for the same reason [legalMoves] is empty then.
     */
    fun isLegal(
        state: GameState,
        move: Move,
    ): Boolean = !state.isOver && LegalMoves.isLegal(state, move)

    /** Whether the side to move is in check. */
    fun isInCheck(state: GameState): Boolean = Attacks.isSideToMoveInCheck(state)

    /**
     * Whether the side to move has no legal move at all in this position.
     *
     * This asks about the position geometry itself, which [terminalResult] must decide
     * before any result exists, so it deliberately does not consult [GameState.isOver]; the
     * game-over guard belongs on the public [legalMoves] / [isLegal] entry points.
     */
    fun hasNoLegalMoves(state: GameState): Boolean = LegalMoves.forSideToMove(state).isEmpty()

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

    /**
     * The draws the side to move may claim by declaring [declaredMove] as its next move.
     *
     * Standard chess lets the player to move claim a threefold repetition or the fifty-move
     * rule on the position its declared move is about to produce, as well as on the one
     * already in front of it. That is the prospective legal-move condition `PRODUCT` and
     * `ARCHITECTURE` require alongside the current one.
     *
     * The declaration binds: [declaredMove] must be legal, and only that exact move counts.
     * A capture or a pawn move clears the repetition history and resets the halfmove clock,
     * so neither can ever create a claim; a move reaching some other position creates only
     * that position; and an illegal move, or any move in a finished game, declares nothing.
     *
     * A declared move that delivers checkmate ends the game as checkmate — [terminalResult]
     * settles that precedence — so it adds no draw claim of its own. Claims [state] already
     * carries stand whatever is declared, because those need no declaration at all.
     */
    fun availableDrawClaims(
        state: GameState,
        declaredMove: Move,
    ): Set<DrawClaim> {
        if (!isLegal(state, declaredMove)) return emptySet()

        val declared = applyMove(state, declaredMove)
        if (declared.result?.reason == TerminationReason.CHECKMATE) return availableDrawClaims(state)

        return buildSet {
            addAll(availableDrawClaims(state))
            if (Repetition.occurrences(declared) >= DrawRuleState.THREEFOLD_REPETITION_COUNT) {
                add(DrawClaim.THREEFOLD_REPETITION)
            }
            if (declared.halfmoveClock >= DrawRuleState.FIFTY_MOVE_HALFMOVES) {
                add(DrawClaim.FIFTY_MOVE_RULE)
            }
        }
    }

    /** Whether [claim] is currently valid. */
    fun canClaimDraw(
        state: GameState,
        claim: DrawClaim,
    ): Boolean = claim in availableDrawClaims(state)

    /** Whether [claim] is valid for the side to move once it declares [declaredMove]. */
    fun canClaimDraw(
        state: GameState,
        claim: DrawClaim,
        declaredMove: Move,
    ): Boolean = claim in availableDrawClaims(state, declaredMove)

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

        return state.copy(result = GameResult.draw(claimReason(claim)))
    }

    /**
     * The state after a valid [claim] declared together with [declaredMove]: a finished
     * game drawn for that reason, from the position the claim was made in.
     *
     * [claim] must be valid for that declaration; check with [canClaimDraw] or
     * [availableDrawClaims] first. [declaredMove] is never played — declaring it is what
     * entitles the claim, and the claim ends the game instead of continuing it.
     */
    fun claimDraw(
        state: GameState,
        claim: DrawClaim,
        declaredMove: Move,
    ): GameState {
        require(!state.isOver) { "The game is over: ${state.result}" }
        require(canClaimDraw(state, claim, declaredMove)) { "No valid $claim claim follows from declaring $declaredMove" }

        return state.copy(result = GameResult.draw(claimReason(claim)))
    }

    /**
     * [state] resigned by [side], which wins the game for the other one.
     *
     * Resignation does not depend on whose turn it is or on the position: a player may
     * give up at any point in a game that is still running. It is not a move, so the
     * history is untouched.
     */
    fun resign(
        state: GameState,
        side: Side,
    ): GameState {
        require(!state.isOver) { "The game is over: ${state.result}" }
        return state.copy(result = GameResult.resignation(loser = side))
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

    /** The draws the side to move may claim in [game] by declaring [declaredMove]. */
    fun availableDrawClaims(
        game: ChessGame,
        declaredMove: Move,
    ): Set<DrawClaim> = availableDrawClaims(game.state, declaredMove)

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
     * [game] drawn by a valid [claim] declared together with [declaredMove]. The declared
     * move is not played, so the move history is unaffected.
     */
    fun claimDraw(
        game: ChessGame,
        claim: DrawClaim,
        declaredMove: Move,
    ): ChessGame = game.copy(state = claimDraw(game.state, claim, declaredMove))

    /** [game] resigned by [side]. The move history is unaffected. */
    fun resign(
        game: ChessGame,
        side: Side,
    ): ChessGame = game.copy(state = resign(game.state, side))

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

    /** The termination a granted [claim] ends the game with. */
    private fun claimReason(claim: DrawClaim): TerminationReason =
        when (claim) {
            DrawClaim.THREEFOLD_REPETITION -> TerminationReason.THREEFOLD_REPETITION_CLAIM
            DrawClaim.FIFTY_MOVE_RULE -> TerminationReason.FIFTY_MOVE_RULE_CLAIM
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
