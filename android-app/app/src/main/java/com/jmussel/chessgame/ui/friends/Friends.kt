package com.jmussel.chessgame.ui.friends

import com.jmussel.chessgame.api.ChessApiException
import com.jmussel.chessgame.api.UserSummaryDto

/**
 * The friends screen: who they are, what is happening, and what the player was last told.
 *
 * One object rather than a state machine, because these things overlap — a list can be on
 * screen while a name is being added, and the message from the last thing that happened
 * stays readable while it is.
 */
data class FriendsUiState(
    /** Everyone the player is friends with, as the server ordered them. */
    val friends: List<UserSummaryDto> = emptyList(),
    /** The list is being fetched; there may be an older list on screen meanwhile. */
    val loading: Boolean = false,
    /** An add, a removal, or an opening is in flight, so nothing else should start. */
    val busy: Boolean = false,
    /** Whether the list has ever arrived, which is what tells "no friends" from "not yet". */
    val loaded: Boolean = false,
    /** The player a lookup found, waiting to be added or dismissed. */
    val found: UserSummaryDto? = null,
    /** The friend a removal is waiting to be confirmed for. */
    val removing: UserSummaryDto? = null,
    /** What the player should read about the last thing that happened. */
    val message: String? = null,
)

/**
 * What the friends screen can ask the app to do.
 *
 * One bundle rather than eight parameters threaded through the shell: they always travel
 * together, and every one of them is the same model's method.
 */
data class FriendsActions(
    val onFind: (String) -> Unit = {},
    val onAdd: (String) -> Unit = {},
    val onDismissFound: () -> Unit = {},
    val onAskToRemove: (UserSummaryDto) -> Unit = {},
    val onConfirmRemove: (UserSummaryDto) -> Unit = {},
    val onCancelRemove: () -> Unit = {},
    val onPlay: (UserSummaryDto) -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * What the friends screen says.
 *
 * Who exists, who is already a friend, and what removing one does are the server's and the
 * database's answers (`D009`, `D013`), so a refusal is repeated in the server's own words
 * rather than re-worded from a status code. Only the things the server has no opinion
 * about — an empty box, a lost connection, and the warning before a removal — are written
 * here.
 *
 * Pure, so the wording is tested without a screen.
 */
object Friends {
    /** [requested] with the spaces around it removed, which is what is sent. */
    fun cleaned(requested: String): String = requested.trim()

    /** Whether there is anything to send at all. */
    fun isSendable(requested: String): Boolean = cleaned(requested).isNotEmpty()

    /** What to show when the server refused. */
    fun messageFor(refusal: ChessApiException): String = refusal.explanation.ifBlank { REFUSED }

    /** What to show when the request never reached the server. */
    fun unreachableMessage(): String = UNREACHABLE

    /**
     * What removing [username] will actually do.
     *
     * Removing a friend does not end the game being played: it finishes, and the series
     * closes after it rather than starting the next one (`D013`). A player about to tap
     * "Remove" is entitled to know that before they do.
     */
    fun removalWarning(username: String): String =
        "Remove $username? A game you are playing now will finish as normal — there just will not be another one."

    private const val REFUSED = "The server would not do that. Try again."
    private const val UNREACHABLE = "Could not reach the server. Check your connection and try again."
}
