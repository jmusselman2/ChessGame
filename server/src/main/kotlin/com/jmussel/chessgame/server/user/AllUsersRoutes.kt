@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.api.toSummaryOrNull
import com.jmussel.chessgame.server.auth.authenticatedUser
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.UserRepository
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.uuid.ExperimentalUuidApi

/** The most people the list holds. Plenty while the only users are testers (`D071`). */
const val ALL_USERS_CAP: Int = 200

/**
 * Everyone the caller could add as a friend: the "All users" testing aid (`D071`).
 *
 * A way round typing an exact username (`D009`) while testing with fresh accounts. It is
 * always on, and is to be removed or restricted to admins before the app has real users.
 * Restricting it means a permission check here and a field in `/me`, both added then.
 *
 * Only people an add would accept are listed: not the caller, not a current friend, and not
 * an account with no username yet. Most recently seen first, so abandoned test accounts sink,
 * but the time itself is not sent: each entry is a [com.jmussel.chessgame.server.api.UserSummary]
 * and nothing more (`D069`). Capped at [ALL_USERS_CAP], with no paging.
 *
 * Routes must sit behind authentication.
 */
fun Route.allUsersRoutes(
    users: UserRepository,
    friendships: FriendshipRepository,
) {
    get("/users") {
        val caller = call.authenticatedUser()
        val excluded = friendships.friendsOf(caller.userId).toSet() + caller.userId

        call.respond(users.namedUsersExcept(excluded, ALL_USERS_CAP).mapNotNull { it.toSummaryOrNull() })
    }
}
