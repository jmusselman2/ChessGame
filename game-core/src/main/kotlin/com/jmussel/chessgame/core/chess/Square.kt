package com.jmussel.chessgame.core.chess

/**
 * A square on the 8x8 board, stored as an index in `0..63`.
 *
 * `index = rank * 8 + file`, where file `0` is the `a` file and rank `0` is rank `1`.
 * Squares are therefore ordered a1, b1, ... h1, a2, ... h8.
 */
@JvmInline
value class Square private constructor(
    val index: Int,
) {
    /** `0..7`, where `0` is the `a` file. */
    val file: Int
        get() = index % FILES

    /** `0..7`, where `0` is rank `1`. */
    val rank: Int
        get() = index / FILES

    /** `'a'..'h'`. */
    val fileChar: Char
        get() = 'a' + file

    /** `1..8`. */
    val rankNumber: Int
        get() = rank + 1

    /** The square's algebraic name, for example `e4`. */
    val name: String
        get() = "$fileChar$rankNumber"

    override fun toString(): String = name

    companion object {
        const val FILES: Int = 8
        const val RANKS: Int = 8
        const val COUNT: Int = FILES * RANKS

        /** Every square, ordered a1, b1, ... h1, a2, ... h8. */
        val ALL: List<Square> = (0 until COUNT).map { Square(it) }

        fun ofIndex(index: Int): Square {
            require(index in 0 until COUNT) { "Square index out of range: $index" }
            return ALL[index]
        }

        fun of(
            file: Int,
            rank: Int,
        ): Square {
            require(file in 0 until FILES) { "File out of range: $file" }
            require(rank in 0 until RANKS) { "Rank out of range: $rank" }
            return ALL[rank * FILES + file]
        }

        /** Returns the square, or `null` when [file] or [rank] is off the board. */
        fun ofOrNull(
            file: Int,
            rank: Int,
        ): Square? = if (file in 0 until FILES && rank in 0 until RANKS) ALL[rank * FILES + file] else null

        /** Parses an algebraic square name such as `e4`. */
        fun parse(name: String): Square = parseOrNull(name) ?: throw IllegalArgumentException("Invalid square name: $name")

        /** Parses an algebraic square name such as `e4`, or returns `null` when it is not one. */
        fun parseOrNull(name: String): Square? {
            if (name.length != 2) return null
            val file = name[0].lowercaseChar() - 'a'
            val rank = name[1] - '1'
            return ofOrNull(file, rank)
        }
    }
}
