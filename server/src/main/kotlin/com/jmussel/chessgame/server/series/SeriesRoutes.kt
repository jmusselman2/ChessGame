@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.server.api.SeriesSummary
import com.jmussel.chessgame.server.auth.authenticatedUser
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.user.Username
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlin.uuid.ExperimentalUuidApi

/**
 * Starting or opening the series with a friend.
 *
 * "Play with this friend" is one action: it opens the pair's active series if there is one
 * and creates it otherwise, so parallel active series never appear (`D011`). The caller
 * must actually be friends with the person named.
 *
 * Routes must sit behind authentication.
 */
fun Route.seriesRoutes(
    users: UserRepository,
    friendships: FriendshipRepository,
    series: GameSeriesRepository,
) {
    post("/series") {
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

        if (friend.id == caller.userId) {
            call.respondText("You cannot play yourself", status = HttpStatusCode.BadRequest)
            return@post
        }

        if (!friendships.areFriends(caller.userId, friend.id)) {
            call.respondText("Not friends with ${friend.username}", status = HttpStatusCode.Forbidden)
            return@post
        }

        val opened = series.openOrCreate(caller.userId, friend.id)

        call.respond(
            status = if (opened.created) HttpStatusCode.Created else HttpStatusCode.OK,
            message = SeriesSummary.of(opened.series, opponent = friend, viewer = caller.userId),
        )
    }
}
