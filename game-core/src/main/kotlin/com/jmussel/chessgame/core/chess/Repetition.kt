package com.jmussel.chessgame.core.chess

/**
 * Repetition of positions: a claimable draw at three occurrences and an automatic one at
 * five.
 *
 * Two positions repeat when the same player is to move, every piece stands on the same
 * square, and the same moves are available — so castling rights count, and so does an
 * en passant capture, but only while one is actually playable.
 */
object Repetition {
    /** The repetition identity of [state]. */
    fun keyOf(state: GameState): PositionKey =
        PositionKey(
            buildString {
                append(state.board.toString().replace("\n", "/"))
                append(' ')
                append(if (state.sideToMove == Side.WHITE) 'w' else 'b')
                append(' ')
                append(state.castlingRights)
                append(' ')
                append(playableEnPassantTarget(state)?.name ?: "-")
            },
        )

    /**
     * The en passant target only when a capture onto it is actually legal. A target no one
     * can use does not make two otherwise identical positions different.
     */
    private fun playableEnPassantTarget(state: GameState): Square? {
        val target = state.enPassantTarget ?: return null
        val usable = EnPassant.availableMoves(state).any { !LegalMoves.leavesOwnKingInCheck(state, it) }
        return if (usable) target else null
    }

    /** How many times [state]'s position has occurred in the current game. */
    fun occurrences(state: GameState): Int = state.drawRuleState.repetitionsOf(keyOf(state))

    /** [state] with its own position counted once more. */
    fun recording(state: GameState): GameState = state.copy(drawRuleState = state.drawRuleState.recording(keyOf(state)))

    /** Whether the side to move may claim a draw by threefold repetition. */
    fun canClaimThreefold(state: GameState): Boolean = !state.isOver && occurrences(state) >= DrawRuleState.THREEFOLD_REPETITION_COUNT

    /** Whether the position has occurred often enough to end the game automatically. */
    fun isFivefold(state: GameState): Boolean = occurrences(state) >= DrawRuleState.FIVEFOLD_REPETITION_COUNT
}
