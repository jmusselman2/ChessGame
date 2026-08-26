@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.auth.authenticatedUser
import com.jmussel.chessgame.server.db.ClaimUsernameResult
import com.jmussel.chessgame.server.db.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlin.uuid.ExperimentalUuidApi

/**
 * Claiming a username.
 *
 * A user picks a name once (`PRODUCT.md`: username changes are outside the MVP), it is
 * unique case-insensitively, and the database has the last word on who wins a race
 * (`D007`). A name is never released, so a lost anonymous account keeps it reserved
 * (`D008`).
 *
 * Routes must sit behind authentication: the claim is always for the calling user, never
 * for a user id the client names.
 */
fun Route.usernameRoutes(users: UserRepository) {
    post("/username") {
        val user = call.authenticatedUser()
        val requested = call.receiveText().trim()

        val username = Username.ofOrNull(requested)
        if (username == null) {
            call.respondText(
                text = describe(Username.problemWith(requested)),
                status = HttpStatusCode.BadRequest,
            )
            return@post
        }

        when (val result = users.claimUsername(user.userId, username)) {
            is ClaimUsernameResult.Claimed ->
                call.respondText(result.user.username.orEmpty(), status = HttpStatusCode.OK)

            ClaimUsernameResult.Taken ->
                call.respondText("That username is taken", status = HttpStatusCode.Conflict)

            is ClaimUsernameResult.AlreadyNamed ->
                call.respondText(
                    text = "You are already ${result.username}; username changes are not supported",
                    status = HttpStatusCode.Conflict,
                )

            ClaimUsernameResult.NoSuchUser ->
                call.respondText("Unknown user", status = HttpStatusCode.Unauthorized)
        }
    }
}

private fun describe(problem: Username.Companion.Problem?): String =
    when (problem) {
        Username.Companion.Problem.TOO_SHORT ->
            "A username needs at least ${Username.MIN_LENGTH} characters"
        Username.Companion.Problem.TOO_LONG ->
            "A username can be at most ${Username.MAX_LENGTH} characters"
        Username.Companion.Problem.DISALLOWED_CHARACTERS ->
            "A username can use letters, numbers, underscore, and hyphen only"
        null -> "Invalid username"
    }
