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
 * One accepted Undo operation, kept so Redo can restore it exactly (`D092`).
 *
 * [records] are the moves the Undo took back, oldest first, each with the position it was
 * played from; [stateAfter] is the position after the last of them, which is the position the
 * Undo left. Together they hold every state the operation passed through, so restoring it
 * replays nothing (`D061`). One Undo is one operation, whether it took back one ply or two
 * (a computer takeback, an accepted undo request): Redo restores it whole.
 */
class UndoneOperation(
    records: List<MoveRecord>,
    val stateAfter: GameState,
) {
    val records: List<MoveRecord> = Collections.unmodifiableList(records.toList())

    init {
        require(this.records.isNotEmpty()) { "An Undo takes back at least one move" }
    }

    /** The moves the operation took back, oldest first. */
    val moves: List<Move>
        get() = records.map { it.move }

    override fun equals(other: Any?): Boolean =
        this === other || (other is UndoneOperation && records == other.records && stateAfter == other.stateAfter)

    override fun hashCode(): Int = 31 * records.hashCode() + stateAfter.hashCode()

    override fun toString(): String = "UndoneOperation(records=$records, stateAfter=$stateAfter)"
}

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
    redoPath: List<UndoneOperation> = emptyList(),
) {
    val history: List<MoveRecord> = Collections.unmodifiableList(history.toList())

    /**
     * The Undo operations Redo can restore, in the order they were undone: the next one to
     * restore is last (`D092`). Undo adds to it and Redo takes from it; any other change to the
     * game's moves or result clears it, because play has diverged from it.
     */
    val redoPath: List<UndoneOperation> = Collections.unmodifiableList(redoPath.toList())

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

    /** Returns a copy with [state], [history] and [redoPath] replaced. */
    fun copy(
        state: GameState = this.state,
        history: List<MoveRecord> = this.history,
        redoPath: List<UndoneOperation> = this.redoPath,
    ): ChessGame = ChessGame(state, history, redoPath)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is ChessGame && state == other.state && history == other.history && redoPath == other.redoPath)

    override fun hashCode(): Int = 31 * (31 * state.hashCode() + history.hashCode()) + redoPath.hashCode()

    override fun toString(): String = "ChessGame(state=$state, history=$history, redoPath=$redoPath)"

    companion object {
        /** A new game from the standard starting position, with no moves played. */
        fun newGame(): ChessGame = ChessGame(StandardPosition.newGame())
    }
}
