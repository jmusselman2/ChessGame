package com.jmussel.chessgame.ui.dashboard

import com.jmussel.chessgame.api.DashboardEntryDto

/**
 * One line under a dashboard heading: who it is against, and where that game is.
 *
 * Nothing here is decided by the app — the server said whose turn it is and which colour
 * the player has, and this only says how to show it.
 */
data class DashboardRow(
    val seriesId: String,
    val gameId: String,
    val opponent: String,
    val detail: String,
)

/**
 * Turning what the server said into the dashboard's sections.
 *
 * The hierarchy is `docs/PRODUCT.md`: the games waiting on the player come first, because
 * they are the only ones the player can do anything about. Their Turn is `M14.2` and
 * Friends is `M14.3`.
 *
 * Pure, so the grouping and wording are tested without a screen.
 */
object DashboardSections {
    /** The games waiting on the player, in the order the server sent them. */
    fun yourTurn(entries: List<DashboardEntryDto>): List<DashboardRow> = entries.filter { it.yourTurn }.mapNotNull(::rowOf)

    /** `"White • Move 18"`, or just the colour before the first move is numbered. */
    fun detailFor(entry: DashboardEntryDto): String {
        val side = entry.yourSide?.let(::sideLabel)
        val move = entry.moveNumber?.let { "Move $it" }

        return listOfNotNull(side, move).joinToString(separator = SEPARATOR)
    }

    private fun rowOf(entry: DashboardEntryDto): DashboardRow? {
        // A series between games has nothing to open yet; it is not a line to tap.
        val gameId = entry.gameId ?: return null

        return DashboardRow(
            seriesId = entry.seriesId,
            gameId = gameId,
            opponent = entry.opponent.username,
            detail = detailFor(entry),
        )
    }

    private fun sideLabel(side: String): String = side.lowercase().replaceFirstChar { it.uppercase() }

    private const val SEPARATOR = " • "
}
