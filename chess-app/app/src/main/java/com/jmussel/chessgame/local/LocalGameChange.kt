package com.jmussel.chessgame.local

import com.jmussel.chessgame.core.chess.ChessGame

/**
 * What happened to a local game between two of its states, as far as the store is
 * concerned: the store writes intents, not whole games (`D084`).
 *
 * The local game screen hands back a whole new state for every tap, and most taps (selecting
 * a piece, opening a prompt) leave the game itself as it was. Comparing the games before and
 * after tells those apart from the three changes the store records.
 */
sealed interface LocalGameChange {
    /** One move was played. */
    data object Move : LocalGameChange

    /** The last [plies] moves were taken back. */
    data class TakeBack(
        val plies: Int,
    ) : LocalGameChange

    /** The game ended without a move: a resignation or a draw claim. */
    data object Result : LocalGameChange

    companion object {
        /**
         * The change from [before] to [after], or `null` when the game did not change.
         *
         * Throws when [after] does not follow from [before] in any of those ways, which
         * would mean the screen replaced the game rather than played it.
         */
        fun between(
            before: ChessGame,
            after: ChessGame,
        ): LocalGameChange? {
            if (before == after) return null
            val earlier = before.history
            val later = after.history
            return when {
                later.size == earlier.size + 1 && later.subList(0, earlier.size) == earlier -> Move
                later.size < earlier.size && earlier.subList(0, later.size) == later -> TakeBack(earlier.size - later.size)
                later == earlier && !before.isOver && after.isOver && after.state.copy(result = null) == before.state -> Result
                else -> throw IllegalArgumentException("The local game was replaced, not played: $before became $after")
            }
        }
    }
}
