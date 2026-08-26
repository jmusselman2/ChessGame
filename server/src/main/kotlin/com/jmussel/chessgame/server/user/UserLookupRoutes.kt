package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.api.toSummaryOrNull
import com.jmussel.chessgame.server.db.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Looking a user up by username, which is how friends are added (`D009`).
 *
 * The match is exact on the normalized name, so `Jordan` and `jordan` find the same person
 * and nothing else does — there is no search, no listing, and no partial match, because
 * the product only needs "I know my friend's name" (`docs/PRODUCT.md`).
 */
fun Route.userLookupRoutes(users: UserRepository) {
    get("/users/{username}") {
        val requested = call.parameters["username"].orEmpty()

        if (Username.ofOrNull(requested) == null) {
            call.respondText("Not a username", status = HttpStatusCode.BadRequest)
            return@get
        }

        val found = users.findByUsername(requested)?.toSummaryOrNull()

        if (found == null) {
            call.respondText("No such user", status = HttpStatusCode.NotFound)
        } else {
            call.respond(found)
        }
    }
}
