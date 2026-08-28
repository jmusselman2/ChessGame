@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.api.toCurrentUser
import com.jmussel.chessgame.server.auth.authenticatedUser
import com.jmussel.chessgame.server.db.UserRepository
import io.ktor.http.HttpStatusCode
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
 */
fun Route.identityRoutes(users: UserRepository) {
    get("/me") {
        val caller = call.authenticatedUser()
        val stored = users.find(caller.userId)

        if (stored == null) {
            call.respondText("Unknown user", status = HttpStatusCode.Unauthorized)
        } else {
            call.respond(stored.toCurrentUser())
        }
    }
}
