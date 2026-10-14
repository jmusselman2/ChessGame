package com.jmussel.chessgame.core.chess

import kotlin.test.assertEquals

/**
 * [actual] is [expected]'s game: the same position and the same moves played.
 *
 * What an Undo restores. The two games may still differ in their Redo paths, because an Undo
 * keeps what it took back for Redo (`D092`); a test of that says so separately.
 */
fun assertSamePlay(
    expected: ChessGame,
    actual: ChessGame,
    message: String? = null,
) {
    assertEquals(expected.state, actual.state, message)
    assertEquals(expected.history, actual.history, message)
}
