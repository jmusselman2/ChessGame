package com.jmussel.chessgame.core.chess

import java.util.Collections

/**
 * One played move together with the position it was played from.
 *
 * Keeping the whole prior position — not a delta — is what makes an undo exact: board,
 * side to move, castling rights, en passant target, both draw counters, and the
 * repetition history all come back as they were.
 */
data class MoveRecord(
    val move: Move,
    val positionBefore: GameState,
)

/**
 * A game in progress: the current position plus the moves that produced it.
 *
 * [GameState] is the position alone. Undo eligibility — who may take a move back, and
 * until when — is a separate rule on top of this history.
 *
 * The history is immutable in both directions, because `List` is a read-only view rather
 * than an immutable type. It is snapshotted, so a caller that keeps the list it passed in
 * cannot append to an existing game; and it is published unmodifiable, because the `plus`
 * and `dropLast` results that normal play builds it from are plain JVM lists a cast could
 * write through. Either way in would have changed [lastMove], [lastMover], and therefore
 * who may take a move back, with no game-state transition behind it.
 */
class ChessGame(
    val state: GameState,
    history: List<MoveRecord> = emptyList(),
) {
    val history: List<MoveRecord> = Collections.unmodifiableList(history.toList())

    val isOver: Boolean
        get() = state.isOver

    val result: GameResult?
        get() = state.result

    val sideToMove: Side
        get() = state.sideToMove

    /** The moves played so far, oldest first. */
    val moves: List<Move>
        get() = history.map { it.move }

    /** The most recently played move, or `null` at the start of the game. */
    val lastMove: Move?
        get() = history.lastOrNull()?.move

    /** The side that played [lastMove], or `null` at the start of the game. */
    val lastMover: Side?
        get() = history.lastOrNull()?.positionBefore?.sideToMove

    /** Returns a copy with [state] and [history] replaced. */
    fun copy(
        state: GameState = this.state,
        history: List<MoveRecord> = this.history,
    ): ChessGame = ChessGame(state, history)

    override fun equals(other: Any?): Boolean = this === other || (other is ChessGame && state == other.state && history == other.history)

    override fun hashCode(): Int = 31 * state.hashCode() + history.hashCode()

    override fun toString(): String = "ChessGame(state=$state, history=$history)"

    companion object {
        /** A new game from the standard starting position, with no moves played. */
        fun newGame(): ChessGame = ChessGame(StandardPosition.newGame())
    }
}
