package com.jmussel.chessgame.ui.dashboard

import com.jmussel.chessgame.api.DashboardEntryDto
import com.jmussel.chessgame.api.UserSummaryDto

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
 * One friend, and the one thing to do about them.
 *
 * [gameId] is the game already under way with them, and `null` when there is none — which
 * is the whole difference between opening a game and starting one.
 */
data class FriendRow(
    val username: String,
    val gameId: String?,
) {
    /** `"Open"` when there is a game to go back to, `"Play"` when there is not. */
    val action: String
        get() = if (gameId == null) PLAY else OPEN

    private companion object {
        const val PLAY = "Play"
        const val OPEN = "Open"
    }
}

/**
 * Turning what the server said into the dashboard's sections.
 *
 * The hierarchy is `docs/PRODUCT.md`: the games waiting on the player come first, because
 * they are the only ones the player can do anything about, the games waiting on the
 * opponent follow, and every friend is reachable at the bottom.
 *
 * Whose turn it is comes from the server and is never worked out here — the app would only
 * be guessing at state it does not own (`D004`). Every active series belongs to exactly one
 * of the two sections, so nothing a player is in can go missing.
 *
 * Pure, so the grouping and wording are tested without a screen.
 */
object DashboardSections {
    /** The games waiting on the player, in the order the server sent them. */
    fun yourTurn(entries: List<DashboardEntryDto>): List<DashboardRow> = rowsOf(entries.filter { it.yourTurn })

    /** The games waiting on the opponent, in the order the server sent them. */
    fun theirTurn(entries: List<DashboardEntryDto>): List<DashboardRow> = rowsOf(entries.filterNot { it.yourTurn })

    /**
     * Every friend, each with the game already under way with them if there is one.
     *
     * All of them are listed, including the ones already above under a turn heading: the
     * section is the way to reach a friend, not a leftovers pile. Friends are ordered by
     * name, because this is a list to find someone in rather than a feed.
     */
    fun friends(
        friends: List<UserSummaryDto>,
        entries: List<DashboardEntryDto>,
    ): List<FriendRow> {
        val gamesByOpponent = entries.mapNotNull { entry -> entry.gameId?.let { entry.opponent.userId to it } }.toMap()

        return friends
            .sortedBy { it.username.lowercase() }
            .map { friend -> FriendRow(username = friend.username, gameId = gamesByOpponent[friend.userId]) }
    }

    /** `"White • Move 18"`, or just the colour before the first move is numbered. */
    fun detailFor(entry: DashboardEntryDto): String {
        val side = entry.yourSide?.let(::sideLabel)
        val move = entry.moveNumber?.let { "Move $it" }

        return listOfNotNull(side, move).joinToString(separator = SEPARATOR)
    }

    private fun rowsOf(entries: List<DashboardEntryDto>): List<DashboardRow> = entries.mapNotNull(::rowOf)

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
