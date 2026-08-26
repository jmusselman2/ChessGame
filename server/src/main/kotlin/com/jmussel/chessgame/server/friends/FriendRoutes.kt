@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.friends

import com.jmussel.chessgame.server.api.toSummaryOrNull
import com.jmussel.chessgame.server.auth.authenticatedUser
import com.jmussel.chessgame.server.db.AddFriendResult
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.user.Username
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.uuid.ExperimentalUuidApi

/**
 * Listing friends and adding one by username.
 *
 * A friendship is mutual the moment it is made, with no request to accept (`D009`). The
 * caller is always one side of it — a client cannot make two other people friends, and can
 * only list its own friends.
 *
 * Routes must sit behind authentication.
 */
fun Route.friendRoutes(
    users: UserRepository,
    friendships: FriendshipRepository,
) {
    get("/friends") {
        val caller = call.authenticatedUser()

        // A friend who has not claimed a username cannot be listed, because there is
        // nothing to show; that cannot happen through this API, which only adds by name.
        val friends = friendships.friendsOf(caller.userId).mapNotNull { users.find(it)?.toSummaryOrNull() }

        call.respond(friends)
    }

    post("/friends") {
        val caller = call.authenticatedUser()
        val requested = call.receiveText().trim()

        if (Username.ofOrNull(requested) == null) {
            call.respondText("Not a username", status = HttpStatusCode.BadRequest)
            return@post
        }

        val friend = users.findByUsername(requested)
        if (friend?.username == null) {
            call.respondText("No such user", status = HttpStatusCode.NotFound)
            return@post
        }

        when (friendships.add(caller.userId, friend.id)) {
            is AddFriendResult.Added ->
                call.respondText(friend.username, status = HttpStatusCode.OK)

            AddFriendResult.AlreadyFriends ->
                call.respondText("Already friends with ${friend.username}", status = HttpStatusCode.Conflict)

            AddFriendResult.Yourself ->
                call.respondText("You cannot add yourself", status = HttpStatusCode.BadRequest)
        }
    }
}
