package com.jmussel.chessgame.ui.groups

import com.jmussel.chessgame.api.ChessApiException
import com.jmussel.chessgame.api.GroupSummaryDto
import com.jmussel.chessgame.api.UserSummaryDto

/**
 * The groups screen: the groups the player is in, and what is happening on it.
 *
 * A group is a named set of people who can all play each other (`D049`, `D076`).
 */
data class GroupsUiState(
    /** The groups the player is in, in the server's order. */
    val groups: List<GroupSummaryDto> = emptyList(),
    /** The list is being fetched; there may be an older list on screen meanwhile. */
    val loading: Boolean = false,
    /** A group is being created, so nothing else should start. */
    val busy: Boolean = false,
    /** Whether the list has ever arrived, which is what tells "no groups" from "not yet". */
    val loaded: Boolean = false,
    /** What the player should read about the last thing that happened. */
    val message: String? = null,
)

/** What the groups screen can ask the app to do. */
data class GroupsActions(
    val onOpen: (GroupSummaryDto) -> Unit = {},
    val onCreate: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * One group: who is in it, which friends could join it, and what is happening on it.
 *
 * The friends are loaded with the members, because who can be added is the difference
 * between the two.
 */
data class GroupUiState(
    /** The group as it was listed or created. Its name heads the screen. */
    val group: GroupSummaryDto? = null,
    /** Everyone in it now, the player included, oldest member first. */
    val members: List<UserSummaryDto> = emptyList(),
    /** The player's friends, of whom those not already members can be added. */
    val friends: List<UserSummaryDto> = emptyList(),
    /** The group is being fetched; there may be an older one on screen meanwhile. */
    val loading: Boolean = false,
    /** An add, a Play or a leave is in flight, so nothing else should start. */
    val busy: Boolean = false,
    /** Whether the members have ever arrived. */
    val loaded: Boolean = false,
    /** The server does not know this group for the player: it is gone, or they left it. */
    val unavailable: Boolean = false,
    /** Leaving is waiting to be confirmed. */
    val confirmingLeave: Boolean = false,
    /** What the player should read about the last thing that happened. */
    val message: String? = null,
)

/** What one group's screen can ask the app to do. */
data class GroupActions(
    val onPlay: (UserSummaryDto) -> Unit = {},
    val onAdd: (UserSummaryDto) -> Unit = {},
    val onAskToLeave: () -> Unit = {},
    val onConfirmLeave: () -> Unit = {},
    val onCancelLeave: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** One member, and whether they are the player, who has nothing to do about themselves. */
data class GroupMemberRow(
    val member: UserSummaryDto,
    val isYou: Boolean,
)

/**
 * What the group screens show and say, decided without a screen so it can be tested.
 *
 * Refusals are repeated in the server's words, like the friends screen's: who may be added
 * and what leaving does are the server's answers (`D049`).
 */
object Groups {
    /**
     * The longest name the server accepts, in characters.
     *
     * The server's `MAX_GROUP_NAME_LENGTH`, and the `groups_name_length` constraint behind it.
     */
    const val MAX_NAME_LENGTH = 48

    /** [requested] with the spaces around it removed, which is what is sent. */
    fun cleanedName(requested: String): String = requested.trim()

    /** Whether [requested] is a name the server would take. */
    fun isCreatable(requested: String): Boolean = cleanedName(requested).length in 1..MAX_NAME_LENGTH

    /** "1 member", "3 members". */
    fun memberCount(count: Int): String = if (count == 1) "1 member" else "$count members"

    /** The members, in the server's order, with the player marked by [ownUserId]. */
    fun memberRows(
        state: GroupUiState,
        ownUserId: String?,
    ): List<GroupMemberRow> = state.members.map { GroupMemberRow(member = it, isYou = it.userId == ownUserId) }

    /** The player's friends who are not in the group yet, in the order the friends list has them. */
    fun addableFriends(state: GroupUiState): List<UserSummaryDto> {
        val memberIds = state.members.map { it.userId }.toSet()

        return state.friends.filterNot { it.userId in memberIds }
    }

    /** What "Add a friend" says when it has nobody to offer, or `null` when it has someone. */
    fun nobodyToAdd(state: GroupUiState): String? =
        when {
            addableFriends(state).isNotEmpty() -> null
            state.friends.isEmpty() -> NO_FRIENDS
            else -> ALL_FRIENDS_IN
        }

    /** The question asked before leaving [name], with what leaving will really do. */
    fun leaveWarning(name: String): String = "Leave $name? Your games with its members carry on."

    /**
     * Whether a refusal means the group is not there for the player any more.
     *
     * The server answers a group it does not know and a group the player is not in with the
     * same `404` (`M19.2`), and a group can only be reached from inside it.
     */
    fun isGone(refusal: ChessApiException): Boolean =
        refusal.status == NOT_FOUND && (refusal.explanation.isBlank() || refusal.explanation == NO_SUCH_GROUP)

    /** What to show when the server refused. */
    fun messageFor(refusal: ChessApiException): String = refusal.explanation.ifBlank { REFUSED }

    /** What to show when the request never reached the server. */
    fun unreachableMessage(): String = UNREACHABLE

    /** What a group that is gone says. */
    const val UNAVAILABLE = "This group is not available."

    private const val NOT_FOUND = 404
    private const val NO_SUCH_GROUP = "No such group"
    private const val NO_FRIENDS = "You have no friends to add yet."
    private const val ALL_FRIENDS_IN = "All your friends are in this group."
    private const val REFUSED = "The server would not do that. Try again."
    private const val UNREACHABLE = "Could not reach the server. Check your connection and try again."
}
