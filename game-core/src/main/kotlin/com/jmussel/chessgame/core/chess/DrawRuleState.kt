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
 * records how often each repetition-relevant position has occurred in the current game;
 * a position that has not occurred simply has no entry, so every recorded count is
 * positive.
 *
 * The state is immutable. `Map` is only a read-only view, so the supplied counts are
 * snapshotted: a caller that keeps its own mutable map cannot change an already-built
 * state — and therefore cannot change a repetition draw — outside a game-state
 * transition.
 */
class DrawRuleState(
    val halfmoveClock: Int = 0,
    positionCounts: Map<PositionKey, Int> = emptyMap(),
) {
    val positionCounts: Map<PositionKey, Int> = positionCounts.toMap()

    init {
        require(halfmoveClock >= 0) { "halfmoveClock cannot be negative: $halfmoveClock" }
        this.positionCounts.forEach { (key, count) ->
            require(count > 0) { "A recorded position has occurred at least once: $key occurred $count times" }
        }
    }

    fun repetitionsOf(key: PositionKey): Int = positionCounts[key] ?: 0

    /** Returns a copy that has seen [key] one more time. */
    fun recording(key: PositionKey): DrawRuleState = DrawRuleState(halfmoveClock, positionCounts + (key to repetitionsOf(key) + 1))

    fun withHalfmoveClock(halfmoveClock: Int): DrawRuleState = DrawRuleState(halfmoveClock, positionCounts)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is DrawRuleState && halfmoveClock == other.halfmoveClock && positionCounts == other.positionCounts)

    override fun hashCode(): Int = 31 * halfmoveClock + positionCounts.hashCode()

    override fun toString(): String = "DrawRuleState(halfmoveClock=$halfmoveClock, positionCounts=$positionCounts)"

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
