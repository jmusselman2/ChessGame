package com.jmussel.chessgame.core.chess

/** Who won a finished game. */
enum class GameOutcome {
    WHITE_WINS,
    BLACK_WINS,
    DRAW,
}

/**
 * Why a game ended.
 *
 * [isDraw] separates the reasons that always end in a draw from the decisive ones, and
 * [requiresClaim] marks the two draws a player must claim explicitly rather than the
 * engine applying them automatically.
 */
enum class TerminationReason(
    val isDraw: Boolean,
    val requiresClaim: Boolean = false,
) {
    CHECKMATE(isDraw = false),
    RESIGNATION(isDraw = false),
    STALEMATE(isDraw = true),
    INSUFFICIENT_MATERIAL(isDraw = true),
    FIVEFOLD_REPETITION(isDraw = true),
    SEVENTY_FIVE_MOVE_RULE(isDraw = true),
    THREEFOLD_REPETITION_CLAIM(isDraw = true, requiresClaim = true),
    FIFTY_MOVE_RULE_CLAIM(isDraw = true, requiresClaim = true),
}

/**
 * The terminal result of a game. A game in progress has no result.
 */
data class GameResult(
    val outcome: GameOutcome,
    val reason: TerminationReason,
) {
    init {
        require(reason.isDraw == (outcome == GameOutcome.DRAW)) {
            "Outcome $outcome is inconsistent with termination reason $reason"
        }
    }

    /** The winning side, or `null` for a draw. */
    val winner: Side?
        get() =
            when (outcome) {
                GameOutcome.WHITE_WINS -> Side.WHITE
                GameOutcome.BLACK_WINS -> Side.BLACK
                GameOutcome.DRAW -> null
            }

    companion object {
        fun win(
            side: Side,
            reason: TerminationReason,
        ): GameResult =
            GameResult(
                if (side == Side.WHITE) GameOutcome.WHITE_WINS else GameOutcome.BLACK_WINS,
                reason,
            )

        fun draw(reason: TerminationReason): GameResult = GameResult(GameOutcome.DRAW, reason)

        /** The result of [loser] resigning. */
        fun resignation(loser: Side): GameResult = win(loser.opposite, TerminationReason.RESIGNATION)

        /** The result of [loser] being checkmated. */
        fun checkmate(loser: Side): GameResult = win(loser.opposite, TerminationReason.CHECKMATE)
    }
}
