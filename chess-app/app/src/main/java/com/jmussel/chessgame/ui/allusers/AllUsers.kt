package com.jmussel.chessgame.ui.allusers

import com.jmussel.chessgame.api.ChessApiException
import com.jmussel.chessgame.api.ListedUserDto

/**
 * The "All users" page: every user, and what has happened on it.
 *
 * A testing aid, always on, and to be removed or restricted to admins before the app has
 * real users (`D071`). Everything for it is in this package, apart from the hooks listed in
 * `M17.5`'s completion note.
 */
data class AllUsersUiState(
    /** Everyone but the player: people to add first, then friends, as the server ordered them. */
    val users: List<ListedUserDto> = emptyList(),
    /** The ids of those added since the page was opened, whose rows now say so. */
    val added: Set<String> = emptySet(),
    /** The id of the person an add is in flight for, so nothing else starts meanwhile. */
    val adding: String? = null,
    /** The list is being fetched. */
    val loading: Boolean = false,
    /** Whether the list has arrived, which is what tells "nobody to add" from "not yet". */
    val loaded: Boolean = false,
    /** What the player should read about the last thing that happened. */
    val message: String? = null,
)

/** What the page can ask the app to do. */
data class AllUsersActions(
    val onAdd: (ListedUserDto) -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** One person on the page, and what their row offers. */
data class AllUsersRow(
    val user: ListedUserDto,
    /** Added since the page was opened: the row says so instead of offering Add. */
    val added: Boolean,
    /** Add can be tapped now: not a friend, not added yet, and no add in flight. */
    val canAdd: Boolean,
)

/** The page's two groups: people who can be added, then friends, each in the server's order. */
data class AllUsersSections(
    val toAdd: List<AllUsersRow>,
    val friends: List<AllUsersRow>,
)

/**
 * What the page shows and says, decided without a screen so it can be tested.
 *
 * A refusal to add is the friends screen's, in the server's words (`D009`), because it is
 * the same add. Only the list's own failures are worded here.
 */
object AllUsers {
    /**
     * Everyone on the page, split into people to add and friends.
     *
     * Someone added on this visit stays where they were, marked, until the page is opened
     * again and the server lists them with the friends.
     */
    fun sections(state: AllUsersUiState): AllUsersSections {
        val rows =
            state.users.map { user ->
                val added = user.userId in state.added
                AllUsersRow(user = user, added = added, canAdd = !user.friend && !added && state.adding == null)
            }

        return AllUsersSections(toAdd = rows.filterNot { it.user.friend }, friends = rows.filter { it.user.friend })
    }

    /**
     * What to show when the server refused the list.
     *
     * A refusal with nothing to say is most likely an app older than the server, asking for
     * a page that has since been removed (`D071`), so the fallback says the list is not
     * there rather than blaming the connection.
     */
    fun messageFor(refusal: ChessApiException): String = refusal.explanation.ifBlank { UNAVAILABLE }

    /** What to show when the request never reached the server. */
    fun unreachableMessage(): String = UNREACHABLE

    private const val UNAVAILABLE = "The list of users is not available."
    private const val UNREACHABLE = "Could not reach the server. Check your connection and try again."
}
