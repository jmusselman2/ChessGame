@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.server.api.GameView
import com.jmussel.chessgame.server.auth.AuthenticatedUser
import com.jmussel.chessgame.server.auth.authenticatedUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A request to play a move: intent, never a board state (`ARCHITECTURE.md` §7). */
@Serializable
data class MakeMoveRequest(
    val expectedVersion: Long,
    val from: String,
    val to: String,
    val promotion: String? = null,
)

/**
 * Reading a game and playing a move in it.
 *
 * The client sends what it wants to happen; [GameCommandService] decides whether it may.
 * Nothing here trusts the request beyond its shape.
 *
 * Routes must sit behind authentication.
 */
fun Route.gameRoutes(commands: GameCommandService) {
    get("/games/{gameId}") {
        val caller = call.authenticatedUser()
        val gameId = call.gameId() ?: return@get

        call.respondTo(commands.load(caller.userId, gameId), caller)
    }

    post("/games/{gameId}/moves") {
        val caller = call.authenticatedUser()
        val gameId = call.gameId() ?: return@post

        val request = call.receive<MakeMoveRequest>()
        val move = request.toMoveOrNull()

        if (move == null) {
            call.respondText("Not a move", status = HttpStatusCode.BadRequest)
            return@post
        }

        call.respondTo(
            commands.makeMove(caller.userId, gameId, request.expectedVersion, move),
            caller,
        )
    }
}

/**
 * Turns a command result into a response.
 *
 * Every refusal that has a game to show sends the canonical state with it, so a client that
 * got something wrong — a stale version above all — can correct itself from the reply
 * without a second request.
 */
private suspend fun ApplicationCall.respondTo(
    result: CommandResult,
    caller: AuthenticatedUser,
) {
    when (result) {
        is CommandResult.Applied ->
            respond(GameView.of(result.game, caller.userId))

        CommandResult.NoSuchGame ->
            respondText("No such game", status = HttpStatusCode.NotFound)

        CommandResult.NotAParticipant ->
            respondText("You are not playing this game", status = HttpStatusCode.Forbidden)

        is CommandResult.GameOver ->
            respond(HttpStatusCode.Conflict, GameView.of(result.game, caller.userId))

        is CommandResult.NotYourTurn ->
            respond(HttpStatusCode.Conflict, GameView.of(result.game, caller.userId))

        is CommandResult.StaleVersion ->
            respond(HttpStatusCode.Conflict, GameView.of(result.game, caller.userId))

        is CommandResult.IllegalMove ->
            respond(HttpStatusCode.UnprocessableEntity, GameView.of(result.game, caller.userId))
    }
}

private suspend fun ApplicationCall.gameId(): Uuid? {
    val raw = parameters["gameId"].orEmpty()
    val parsed = runCatching { Uuid.parse(raw) }.getOrNull()

    if (parsed == null) {
        respondText("Not a game id", status = HttpStatusCode.BadRequest)
    }
    return parsed
}

private fun MakeMoveRequest.toMoveOrNull(): Move? {
    val fromSquare = Square.parseOrNull(from) ?: return null
    val toSquare = Square.parseOrNull(to) ?: return null
    val promotionPiece =
        promotion?.let { name ->
            PieceType.PROMOTION_CHOICES.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: return null
        }

    return runCatching { Move(fromSquare, toSquare, promotionPiece) }.getOrNull()
}
