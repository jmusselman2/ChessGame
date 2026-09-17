@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.server.api.SeriesOffer
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
 * `POST /series` with a username starts a series and its first game, and answers `201` with
 * it. When the pair already has an active series nothing is started: the answer is `409` with
 * a [SeriesOffer] listing them, so the app can offer opening one or starting another rather
 * than silently reusing one (`D053`). `POST /series?another=true` is the player choosing
 * another, and always starts one.
 *
 * The server does not check that the two are friends (`D046`). The app only ever offers
 * people the player already knows, and that selection is the gate; re-deciding it here
 * bought nothing except a check that could be raced. This is the one place the client is
 * trusted for a state assertion, and it is deliberate and narrow.
 *
 * `POST /series/{seriesId}/leave` is a player leaving a series, which ends it (`D052`). It is
 * a separate action from resigning a game, and it touches no game: the series' current game
 * can still be finished, and no rematch follows it (`D068`). Leaving twice is not an error —
 * the answer is the ended series either way. A series the caller is not in is reported as not
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

        val startAnother = call.request.queryParameters[ANOTHER] == "true"

        when (val outcome = series.play(caller.userId, friend.id, startAnother)) {
            is PlayOutcome.Offered ->
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message =
                        SeriesOffer(
                            existing = outcome.existing.map { SeriesSummary.of(it, opponent = friend, viewer = caller.userId) },
                        ),
                )

            is PlayOutcome.Started -> {
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

                call.respond(
                    status = HttpStatusCode.Created,
                    message = SeriesSummary.of(outcome.series, opponent = friend, viewer = caller.userId),
                )
            }
        }
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
                // The other player's dashboard is the one thing that changed for them: their
                // game is still there, but it is now the last one. A push naming that game is
                // what makes their app reload (`D022`); the leaver's other devices hear too.
                // Only when this request ended the series — a repeat changed nothing.
                val lastGame = outcome.currentGame
                if (outcome.endedNow && lastGame != null) {
                    realtime.publish(
                        userIds = outcome.series.participants.userIds,
                        message = RealtimeMessage.gameUpdated(lastGame.id, lastGame.version),
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

/** The query parameter that asks for another series when the pair already has one. */
private const val ANOTHER = "another"
