package com.jmussel.chessgame.core.chess

/**
 * A complete chess position: everything the rules need to decide what may happen next.
 *
 * This is the pure game state only. Users, series, versions, persistence, and transport
 * live outside `game-core`.
 */
data class GameState(
    val board: Board,
    val sideToMove: Side,
    val castlingRights: CastlingRights,
    /** The square a pawn may capture onto by en passant this move, or `null`. */
    val enPassantTarget: Square? = null,
    val drawRuleState: DrawRuleState = DrawRuleState(),
    /** Increments after each Black move, starting at 1, as in standard notation. */
    val fullmoveNumber: Int = 1,
    /** Set once the game has ended; `null` while it is in progress. */
    val result: GameResult? = null,
) {
    init {
        require(fullmoveNumber >= 1) { "fullmoveNumber starts at 1: $fullmoveNumber" }
    }

    val isOver: Boolean
        get() = result != null

    val halfmoveClock: Int
        get() = drawRuleState.halfmoveClock
}
