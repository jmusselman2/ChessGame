@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.api.toCurrentUser
import com.jmussel.chessgame.server.auth.authenticatedUser
import com.jmussel.chessgame.server.db.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.uuid.ExperimentalUuidApi

/**
 * Who the caller is.
 *
 * The id is the one everything else in the API references and never changes for an account
 * (`D006`); the username is `null` until it is claimed, which is the whole question the app
 * asks on startup — a returning player goes to the dashboard, a new one to onboarding. The
 * answer is always about the token's own user; there is no id to name in the request.
 *
 * ### Session start
 *
 * This route **is** a session starting, which is why `last_login_at` is written here
 * (`M19.11`). Every other authenticated route is a session being *used*: the app restores
 * or creates its anonymous session and then asks this one who it belongs to, so this is the
 * only place the server can tell the two apart. `last_seen_at` still records the same
 * request as ordinary activity through the auth plugin, throttled (`D010`) — the two
 * answer different questions and neither replaces the other.
 *
 * The write is not throttled, because a session start is discrete and infrequent and an
 * approximate one would answer nothing. It is also not part of the response: a failure to
 * record engagement must not stop a player getting into the app.
 */
fun Route.identityRoutes(users: UserRepository) {
    get("/me") {
        val caller = call.authenticatedUser()
        val stored = users.find(caller.userId)

        if (stored == null) {
            call.respondText("Unknown user", status = HttpStatusCode.Unauthorized)
            return@get
        }

        // Recorded before responding but deliberately not allowed to affect the response.
        // Engagement data is worth having and worth nothing next to being able to play.
        try {
            users.touchLastLogin(caller.userId)
        } catch (failure: Exception) {
            call.application.log.warn("Could not record the session start for user ${caller.userId}", failure)
        }

        call.respond(stored.toCurrentUser())
    }
}
