package com.jmussel.chessgame.ui.history

import com.jmussel.chessgame.api.ChessApiException
import com.jmussel.chessgame.api.SeriesHistoryDto

/**
 * The history screen: what has been played, and what is happening to the list of it.
 *
 * [loaded] is what tells "nothing finished yet" from "not fetched yet": a player who has
 * finished no games has a real answer, and it must not look like a failed load.
 */
data class HistoryUiState(
    /** The series with finished games in them, in the order the server sent them. */
    val series: List<SeriesHistoryDto> = emptyList(),
    /** A load is in flight; whatever arrived last stays on screen meanwhile. */
    val loading: Boolean = false,
    /** Whether the history has ever arrived. */
    val loaded: Boolean = false,
    /** What the player should read about the last thing that happened. */
    val message: String? = null,
)

/**
 * What the history screen says when something goes wrong.
 *
 * The refusal itself is the server's to explain (`D004`); only a lost connection is worded
 * here, because there is no server sentence to repeat.
 *
 * Pure, so the wording is tested without a screen.
 */
object HistoryMessages {
    /** What to show when the server refused. */
    fun messageFor(refusal: ChessApiException): String = refusal.explanation.ifBlank { REFUSED }

    /** What to show when the request never reached the server. */
    fun unreachableMessage(): String = UNREACHABLE

    private const val REFUSED = "The server would not answer that. Try again."
    private const val UNREACHABLE = "Could not reach the server. Check your connection and try again."
}
