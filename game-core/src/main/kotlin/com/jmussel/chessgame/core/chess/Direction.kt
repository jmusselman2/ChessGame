package com.jmussel.chessgame.core.chess

/**
 * A single step across the board, in files and ranks.
 *
 * Directions are absolute: `NORTH` always means towards rank 8, for either side.
 */
data class Direction(
    val fileStep: Int,
    val rankStep: Int,
) {
    companion object {
        val NORTH = Direction(0, 1)
        val SOUTH = Direction(0, -1)
        val EAST = Direction(1, 0)
        val WEST = Direction(-1, 0)
        val NORTH_EAST = Direction(1, 1)
        val NORTH_WEST = Direction(-1, 1)
        val SOUTH_EAST = Direction(1, -1)
        val SOUTH_WEST = Direction(-1, -1)

        /** The four rook directions. */
        val ORTHOGONAL: List<Direction> = listOf(NORTH, SOUTH, EAST, WEST)

        /** The four bishop directions. */
        val DIAGONAL: List<Direction> = listOf(NORTH_EAST, NORTH_WEST, SOUTH_EAST, SOUTH_WEST)

        /** All eight queen and king directions. */
        val ALL: List<Direction> = ORTHOGONAL + DIAGONAL
    }
}

/** The square one [direction] step away, or `null` when that step leaves the board. */
fun Square.shifted(direction: Direction): Square? = Square.ofOrNull(file + direction.fileStep, rank + direction.rankStep)
