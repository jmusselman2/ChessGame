package com.jmussel.chessgame.ai

import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Move

/** How strongly the computer plays: the four levels `D089` sets, weakest first. */
enum class Difficulty(
    /** How the level is stored with a local game and shown to the player, 1 (weakest) to 4. */
    val level: Int,
) {
    VERY_EASY(1),
    EASY(2),
    MEDIUM(3),
    HARD(4),
    ;

    companion object {
        /** The difficulty stored as [level]. */
        fun ofLevel(level: Int): Difficulty =
            entries.firstOrNull { it.level == level } ?: throw IllegalArgumentException("No difficulty level $level")
    }
}

/**
 * Chooses the computer's move in a game of chess (`D086`).
 *
 * Chess-specific on purpose: this is the seam that lets the app's tests use a fake engine
 * and lets a later engine replace the project's own. It is not a rules engine, and not a
 * step toward a cross-game engine abstraction (`D044`).
 *
 * The contract every engine keeps:
 * - On an unfinished position with a legal move, it returns a legal move.
 * - It settles on a legal fallback before searching deeper, so running out of time still
 *   yields a legal move.
 * - It stops when [chooseMove]'s `isCancelled` says so, and then returns `null`: a
 *   cancelled search has no move to apply.
 * - It blocks while it thinks, so it runs off the main thread; the caller decides where.
 * - Any randomness it uses is seedable.
 * - It is never asked to move in a finished game, and refuses if it is.
 */
interface ChessEngine {
    /**
     * A move for the side to move in [state], at [difficulty], or `null` when
     * [isCancelled] turned true before the engine settled.
     *
     * [state] must be unfinished. [isCancelled] is polled while searching and must be cheap
     * and safe to call from the thread the engine runs on.
     */
    fun chooseMove(
        state: GameState,
        difficulty: Difficulty,
        isCancelled: () -> Boolean = { false },
    ): Move?
}
