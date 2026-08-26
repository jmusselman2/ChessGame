package com.jmussel.chessgame.core.chess

/**
 * The two chess sides. `WHITE` always moves first in a standard game.
 */
enum class Side {
    WHITE,
    BLACK,
    ;

    val opposite: Side
        get() = if (this == WHITE) BLACK else WHITE
}
