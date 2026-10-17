package com.jmussel.chessgame.ui.dashboard

import com.jmussel.chessgame.api.DashboardEntryDto
import com.jmussel.chessgame.api.FriendDto
import com.jmussel.chessgame.api.PendingRequestDto
import com.jmussel.chessgame.api.RecordDto

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
    /** Whose row it is, which is how their profile is opened (`D099`). */
    val userId: String = "",
    /** The player's lifetime chess record against this friend, as the server counted it (`D096`). */
    val record: RecordDto = RecordDto(wins = 0, losses = 0, draws = 0),
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
    /**
     * The games waiting on the player, in the order the server sent them: their move, or an
     * undo request for them to answer (`D091`).
     */
    fun yourTurn(entries: List<DashboardEntryDto>): List<DashboardRow> = rowsOf(entries.filter { it.waitingOnYou })

    /** The games waiting on the opponent, in the order the server sent them. */
    fun theirTurn(entries: List<DashboardEntryDto>): List<DashboardRow> = rowsOf(entries.filterNot { it.waitingOnYou })

    /**
     * Every friend, each with the game already under way with them if there is one.
     *
     * All of them are listed, including the ones already above under a turn heading: the
     * section is the way to reach a friend, not a leftovers pile. Friends are ordered by
     * name, because this is a list to find someone in rather than a feed.
     */
    fun friends(
        friends: List<FriendDto>,
        entries: List<DashboardEntryDto>,
    ): List<FriendRow> {
        val gamesByOpponent =
            entries
                .mapNotNull { entry -> entry.gameId?.let { entry.opponent.userId to it } }
                .toMap()

        return friends
            .sortedBy { it.username.lowercase() }
            .map { friend ->
                FriendRow(
                    username = friend.username,
                    gameId = gamesByOpponent[friend.userId],
                    userId = friend.userId,
                    record = friend.record,
                )
            }
    }

    /**
     * `"White • Move 18"`, or just the colour before the first move is numbered, then the undo
     * requests pending, undo request first (`D091`, `D097`): `"• Jordan asks to undo"` or
     * `"• Jordan offers a draw"` for the player who must answer, `"• Undo requested"` or
     * `"• Draw offered"` for the one who asked.
     */
    fun detailFor(entry: DashboardEntryDto): String {
        val side = entry.yourSide?.let(::sideLabel)
        val move = entry.moveNumber?.let { "Move $it" }
        val requests =
            entry.pendingRequests.mapNotNull { pending ->
                val opponent = entry.opponent.username
                when (pending.kind) {
                    PendingRequestDto.UNDO -> if (pending.waitingOnYou) "$opponent $ASKS_TO_UNDO" else UNDO_REQUESTED
                    PendingRequestDto.DRAW -> if (pending.waitingOnYou) "$opponent $OFFERS_A_DRAW" else DRAW_OFFERED
                    else -> null
                }
            }

        return (listOfNotNull(side, move) + requests).joinToString(separator = SEPARATOR)
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

    /** The compact record on a friend row (`D096`): `"2-1-1 W-L-D"`, a real `0-0-0` included. */
    fun recordText(record: RecordDto): String = "${record.wins}-${record.losses}-${record.draws} W-L-D"

    /** What a screen reader says for [recordText]: `"2 wins, 1 loss, 1 draw"`. */
    fun recordDescription(record: RecordDto): String =
        listOf(
            counted(record.wins, "win", "wins"),
            counted(record.losses, "loss", "losses"),
            counted(record.draws, "draw", "draws"),
        ).joinToString(", ")

    private fun counted(
        count: Int,
        one: String,
        many: String,
    ): String = "$count ${if (count == 1) one else many}"

    /**
     * What build the player is holding, in words they can send back.
     *
     * A beta report is only actionable if it names a build (`M17.2`). The tester cannot read
     * a `versionCode` anywhere on the phone, and "the one you sent me last week" is not an
     * answer, so the dashboard says it out loud. Formatted here rather than in the composable
     * for the same reason the status line is (`D041`): a string a report depends on is worth
     * a test.
     */
    fun buildLabel(versionName: String): String = "$BUILD_PREFIX$versionName"

    private const val SEPARATOR = " • "
    private const val ASKS_TO_UNDO = "asks to undo"
    private const val UNDO_REQUESTED = "Undo requested"
    private const val OFFERS_A_DRAW = "offers a draw"
    private const val DRAW_OFFERED = "Draw offered"
    private const val BUILD_PREFIX = "Build "
}
