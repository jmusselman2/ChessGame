@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.server.api.SeriesSummary
import com.jmussel.chessgame.server.auth.authenticatedUser
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.db.userIds
import com.jmussel.chessgame.server.realtime.RealtimeHub
import com.jmussel.chessgame.server.realtime.RealtimeMessage
import com.jmussel.chessgame.server.user.Username
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Playing a friend.
 *
 * `POST /series` with a username opens the pair's one active series (`D095`): `201` with a new
 * series and its first game when they had none, `200` with the one they have otherwise.
 * `POST /series?another=true` was how an app from before `D095` started a parallel series; it
 * is refused with `409` and an explanation that the app needs updating, and starts nothing.
 *
 * The server does not check that the two are friends (`D046`). The app only ever offers
 * people the player already knows, and that selection is the gate; re-deciding it here
 * bought nothing except a check that could be raced. This is the one place the client is
 * trusted for a state assertion, and it is deliberate and narrow. It covers who a player may
 * play, not whether they can be seen: a caller who has not claimed a username is refused with
 * `403`, because both players must be able to find the game (`D081`).
 *
 * `POST /series/{seriesId}/leave` is a player leaving a series, which ends the series and its
 * current game together (`D095`): a game a move was ever played in is the leaver's resignation,
 * and one nobody moved in is deleted. No rematch follows. Leaving twice is not an error — the
 * answer is the ended series either way. A series the caller is not in is reported as not
 * found, like a group (`D049`).
 *
 * Routes must sit behind authentication.
 */
fun Route.seriesRoutes(
    users: UserRepository,
    series: SeriesService,
    realtime: RealtimeHub,
) {
    post("/series") {
        val caller = call.authenticatedUser()

        // A caller with no username would be the opponent in a game the other player cannot
        // see: their dashboard can only show people who have a name (`D081`, as `D045`).
        if (users.find(caller.userId)?.username == null) {
            call.respondText("Claim a username before starting a game", status = HttpStatusCode.Forbidden)
            return@post
        }

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

        // An app from before `D095` asking for a second series alongside the first. There is no
        // compatibility path that could create one.
        if (call.request.queryParameters.contains(ANOTHER)) {
            call.respondText(UPDATE_REQUIRED, status = HttpStatusCode.Conflict)
            return@post
        }

        val outcome = series.play(caller.userId, friend.id)

        if (outcome.started) {
            // A game the other player did not ask for is the one thing they cannot find
            // out for themselves: nothing they did caused it, and until they hear, their
            // dashboard does not show it. Moves announce themselves already
            // (`GameRoutes`), so without this the gap lasts until their next app start --
            // and when the coin toss (`D014`) made them White, it is their move they are
            // not being shown. The caller is told too, for the same reason moves tell both
            // sides: a second device of theirs may be open.
            outcome.series.currentGameId?.let { gameId ->
                realtime.publish(
                    userIds = listOf(caller.userId, friend.id),
                    message = RealtimeMessage.gameUpdated(gameId, NEW_GAME_VERSION),
                )
            }
        }

        call.respond(
            status = if (outcome.started) HttpStatusCode.Created else HttpStatusCode.OK,
            message = SeriesSummary.of(outcome.series, opponent = friend, viewer = caller.userId),
        )
    }

    post("/series/{seriesId}/leave") {
        val caller = call.authenticatedUser()
        val seriesId = runCatching { Uuid.parse(call.parameters["seriesId"].orEmpty()) }.getOrNull()

        if (seriesId == null) {
            call.respondText("Not a series id", status = HttpStatusCode.BadRequest)
            return@post
        }

        when (val outcome = series.leave(caller.userId, seriesId)) {
            LeaveOutcome.NoSuchSeries ->
                call.respondText("No such series", status = HttpStatusCode.NotFound)

            is LeaveOutcome.Left -> {
                // The other player's game has ended under them, or gone. A push naming that game
                // is what makes their app reload it or the dashboard (`D022`); the leaver's other
                // devices hear too. Only when this request ended the series — a repeat changed
                // nothing.
                val changed = outcome.deletedGame?.let { it.id to it.version + 1 } ?: outcome.currentGame?.let { it.id to it.version }
                if (outcome.endedNow && changed != null) {
                    realtime.publish(
                        userIds = outcome.series.participants.userIds,
                        message = RealtimeMessage.gameUpdated(changed.first, changed.second),
                    )
                }

                val opponent =
                    requireNotNull(users.find(outcome.series.opponentOf(caller.userId))) { "A series always has two players" }

                call.respond(SeriesSummary.of(outcome.series, opponent = opponent, viewer = caller.userId))
            }
        }
    }
}

/**
 * The version a game is created at, before any command has moved it on (`D021`).
 *
 * The message carries it because every realtime message does; a client reloads over HTTPS
 * and never treats the version as state (`D022`), so nothing depends on it being current
 * by the time it arrives.
 */
private const val NEW_GAME_VERSION: Long = 0

/** The query parameter an app from before `D095` sent to start a second series with a player. */
private const val ANOTHER = "another"

/** What an app asking for a second series is told (`D095`). */
private const val UPDATE_REQUIRED: String =
    "You can have one series with a player at a time. Update the app to open the one you have."
