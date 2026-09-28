package com.jmussel.chessgame.ui.dashboard

import com.jmussel.chessgame.api.ChessApiException
import com.jmussel.chessgame.api.DashboardEntryDto

/**
 * The home screen: what the server said, and what is happening to it.
 *
 * [loaded] is what tells "no games" from "not yet": an empty dashboard is a real answer
 * for a player with no games, and must not look like a failed load.
 */
data class DashboardUiState(
    /** The active series, in the order the server sent them. */
    val entries: List<DashboardEntryDto> = emptyList(),
    /** A load is in flight; whatever arrived last stays on screen meanwhile. */
    val loading: Boolean = false,
    /** Whether the dashboard has ever arrived. */
    val loaded: Boolean = false,
    /** An opening is in flight, so a second tap should not start another. */
    val busy: Boolean = false,
    /** What the player should read about the last thing that happened. */
    val message: String? = null,
)

/**
 * What the dashboard can ask the app to do.
 *
 * One bundle rather than three parameters threaded through the shell, for the same reason
 * as the friends screen's: they always travel together and all come from one model.
 */
data class DashboardActions(
    val onOpenGame: (DashboardRow) -> Unit = {},
    val onPlayFriend: (FriendRow) -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * What the dashboard says when something goes wrong.
 *
 * The refusal itself is the server's to explain (`D004`); only a lost connection is worded
 * here, because there is no server sentence to repeat.
 *
 * Pure, so the wording is tested without a screen.
 */
object DashboardMessages {
    /** What to show when the server refused. */
    fun messageFor(refusal: ChessApiException): String = refusal.explanation.ifBlank { REFUSED }

    /** What to show when the request never reached the server. */
    fun unreachableMessage(): String = UNREACHABLE

    private const val REFUSED = "The server would not answer that. Try again."
    private const val UNREACHABLE = "Could not reach the server. Check your connection and try again."
}
