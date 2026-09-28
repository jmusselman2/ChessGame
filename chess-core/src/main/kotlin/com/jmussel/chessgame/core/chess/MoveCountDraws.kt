package com.jmussel.chessgame.core.chess

/**
 * The draws counted in moves without progress.
 *
 * [DrawRuleState.halfmoveClock] counts plies since the last pawn move or capture. Fifty
 * such moves — 100 plies — make a draw claimable; seventy-five draw the game
 * automatically.
 */
object MoveCountDraws {
    /** Whether the side to move may claim a draw under the fifty-move rule. */
    fun canClaimFiftyMove(state: GameState): Boolean = !state.isOver && state.halfmoveClock >= DrawRuleState.FIFTY_MOVE_HALFMOVES

    /**
     * Whether the seventy-five-move rule has been reached.
     *
     * A move that delivers checkmate still ends the game as checkmate;
     * [ChessRules.terminalResult] checks that first.
     */
    fun isSeventyFiveMoveDraw(state: GameState): Boolean = state.halfmoveClock >= DrawRuleState.SEVENTY_FIVE_MOVE_HALFMOVES
}
