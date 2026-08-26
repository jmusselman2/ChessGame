package com.jmussel.chessgame.core.chess

/**
 * Identity of a position for repetition purposes: piece placement, side to move,
 * castling rights, and the en passant target square.
 *
 * The engine produces the concrete key; this type only gives it a name so repetition
 * counting cannot be confused with any other string.
 */
@JvmInline
value class PositionKey(
    val value: String,
) {
    override fun toString(): String = value
}

/** A draw a player may claim, as opposed to one the engine applies automatically. */
enum class DrawClaim {
    THREEFOLD_REPETITION,
    FIFTY_MOVE_RULE,
}

/**
 * Bookkeeping for the repetition and move-count draw rules.
 *
 * [halfmoveClock] counts plies since the last pawn move or capture. [positionCounts]
 * records how often each repetition-relevant position has occurred in the current game.
 */
data class DrawRuleState(
    val halfmoveClock: Int = 0,
    val positionCounts: Map<PositionKey, Int> = emptyMap(),
) {
    init {
        require(halfmoveClock >= 0) { "halfmoveClock cannot be negative: $halfmoveClock" }
    }

    fun repetitionsOf(key: PositionKey): Int = positionCounts[key] ?: 0

    /** Returns a copy that has seen [key] one more time. */
    fun recording(key: PositionKey): DrawRuleState = copy(positionCounts = positionCounts + (key to repetitionsOf(key) + 1))

    fun withHalfmoveClock(halfmoveClock: Int): DrawRuleState = copy(halfmoveClock = halfmoveClock)

    companion object {
        /** Occurrences of one position that entitle a player to claim a draw. */
        const val THREEFOLD_REPETITION_COUNT: Int = 3

        /** Occurrences of one position that draw the game automatically. */
        const val FIVEFOLD_REPETITION_COUNT: Int = 5

        /** Halfmoves without a pawn move or capture that entitle a player to claim a draw. */
        const val FIFTY_MOVE_HALFMOVES: Int = 100

        /** Halfmoves without a pawn move or capture that draw the game automatically. */
        const val SEVENTY_FIVE_MOVE_HALFMOVES: Int = 150
    }
}
