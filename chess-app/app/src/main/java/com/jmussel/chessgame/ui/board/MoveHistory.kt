package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.Side

/**
 * One half-move of a move list as the screen shows it (`D105`, `M23.2`): the [ply] it is, from
 * 1, which numbered [moveNumber] it belongs to, the [side] that played it, its required [san]
 * (`M22.7`), and whether it is the [current] move, the one that produced the position on the
 * board.
 */
data class MoveHistoryEntry(
    val ply: Int,
    val moveNumber: Int,
    val side: Side,
    val san: String,
    val current: Boolean,
)

/** One numbered row: White's move, and Black's reply once it is shown. */
data class MoveHistoryRow(
    val number: Int,
    val white: MoveHistoryEntry,
    val black: MoveHistoryEntry?,
) {
    /** The row as written down, `"1. e4 e5"` or `"12. Nf3"`. */
    val text: String
        get() = listOfNotNull("$number.", white.san, black?.san).joinToString(" ")

    /** The row's move that is current, if either is. */
    val current: MoveHistoryEntry?
        get() = listOfNotNull(white, black).firstOrNull { it.current }
}

/**
 * The move list a game screen shows (`D105`): numbered White and Black pairs, with exactly one
 * current move whenever any move is shown, and none when no move is.
 *
 * Built from typed moves, never from rendered text: local and computer games from their active
 * `MoveRecord`s, online games from the server's SAN projection, and Review from its projected
 * moves through the ply on the board. Display only; nothing here is stored.
 */
data class MoveHistory(
    val rows: List<MoveHistoryRow>,
) {
    /** Every shown move, oldest first. */
    val entries: List<MoveHistoryEntry>
        get() = rows.flatMap { listOfNotNull(it.white, it.black) }

    /** The move that produced the position on the board, or `null` when no move is shown. */
    val current: MoveHistoryEntry?
        get() = rows.lastOrNull()?.current

    /** The index in [rows] of the row holding [current], or `null`. */
    val currentRow: Int?
        get() = rows.indexOfLast { it.current != null }.takeIf { it >= 0 }

    /** Each row as written down, for the compact one-line display (`M22.7`). */
    val lines: List<String>
        get() = rows.map { it.text }

    companion object {
        val EMPTY = MoveHistory(emptyList())

        /**
         * A live game's list (`D105`): every move in [sans], its active history in SAN, oldest
         * first, with the last one current. Every game starts from the standard position, so the
         * first move is White's move 1.
         */
        fun live(sans: List<String>): MoveHistory = of(sans)

        /**
         * A finished game's list at review ply [ply] (`D105`): only the moves up to the position
         * on the board, the last of them current. Ply 0, the start, shows none; a review stopped
         * after White's move shows that row with White's move alone.
         */
        fun review(
            sans: List<String>,
            ply: Int,
        ): MoveHistory {
            require(ply in 0..sans.size) { "The game has ${sans.size} moves; there is no ply $ply" }
            return of(sans.take(ply))
        }

        private fun of(sans: List<String>): MoveHistory =
            MoveHistory(
                sans
                    .mapIndexed { index, san ->
                        MoveHistoryEntry(
                            ply = index + 1,
                            moveNumber = index / 2 + 1,
                            side = if (index % 2 == 0) Side.WHITE else Side.BLACK,
                            san = san,
                            current = index == sans.lastIndex,
                        )
                    }.chunked(2)
                    .map { pair -> MoveHistoryRow(number = pair.first().moveNumber, white = pair.first(), black = pair.getOrNull(1)) },
            )
    }
}
