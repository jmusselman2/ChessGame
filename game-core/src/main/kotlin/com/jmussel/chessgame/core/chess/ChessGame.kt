package com.jmussel.chessgame.core.chess

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
 */
data class ChessGame(
    val state: GameState,
    val history: List<MoveRecord> = emptyList(),
) {
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

    companion object {
        /** A new game from the standard starting position, with no moves played. */
        fun newGame(): ChessGame = ChessGame(StandardPosition.newGame())
    }
}
