@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.auth.authenticatedUser
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.StoredUser
import com.jmussel.chessgame.server.db.UserRepository
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi

/** The most people the list holds. Plenty while the only users are testers (`D071`). */
const val ALL_USERS_CAP: Int = 200

/**
 * One person on the "All users" page, and whether the caller is already their friend.
 *
 * Built field by field (`D069`): a user summary plus the one thing the page needs to know.
 */
@Serializable
data class ListedUser(
    val userId: String,
    val username: String,
    val friend: Boolean,
)

/**
 * Every user, for the "All users" testing aid (`D071`).
 *
 * A way round typing an exact username (`D009`) while testing with fresh accounts. It is
 * always on, and is to be removed or restricted to admins before the app has real users.
 * Restricting it means a permission check here and a field in `/me`, both added then.
 *
 * Everyone with a username except the caller: people the caller could add first, then the
 * caller's friends, each most recently seen first so abandoned test accounts sink. The time
 * itself is not sent. Capped at [ALL_USERS_CAP], filled with people to add before friends,
 * with no paging.
 *
 * Routes must sit behind authentication.
 */
fun Route.allUsersRoutes(
    users: UserRepository,
    friendships: FriendshipRepository,
) {
    get("/users") {
        val caller = call.authenticatedUser()
        val friends = friendships.friendsOf(caller.userId).toSet()

        val others = users.namedUsers(within = null, excluded = friends + caller.userId, limit = ALL_USERS_CAP)
        val friendRows = users.namedUsers(within = friends, excluded = emptySet(), limit = ALL_USERS_CAP - others.size)

        call.respond(others.map { it.listed(friend = false) } + friendRows.map { it.listed(friend = true) })
    }
}

private fun StoredUser.listed(friend: Boolean): ListedUser =
    ListedUser(userId = id.toString(), username = requireNotNull(username), friend = friend)
