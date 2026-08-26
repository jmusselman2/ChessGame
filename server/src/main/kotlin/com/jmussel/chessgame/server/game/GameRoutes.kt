@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.server.api.CommandRejection
import com.jmussel.chessgame.server.api.GameView
import com.jmussel.chessgame.server.api.RejectionReason
import com.jmussel.chessgame.server.auth.AuthenticatedUser
import com.jmussel.chessgame.server.auth.authenticatedUser
import com.jmussel.chessgame.server.db.StoredGame
import com.jmussel.chessgame.server.realtime.RealtimeHub
import com.jmussel.chessgame.server.realtime.RealtimeMessage
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

/** A request to take back the caller's latest move (`D016`). */
@Serializable
data class UndoMoveRequest(
    val expectedVersion: Long,
)

/** A request to claim a draw the position allows (`D019`). */
@Serializable
data class ClaimDrawRequest(
    val expectedVersion: Long,
    val claim: String,
)

/**
 * Reading a game, playing a move in it, taking one back, and claiming a draw.
 *
 * The client sends what it wants to happen; [GameCommandService] decides whether it may.
 * Nothing here trusts the request beyond its shape.
 *
 * Routes must sit behind authentication.
 */
fun Route.gameRoutes(
    commands: GameCommandService,
    realtime: RealtimeHub,
) {
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
            commands.makeMove(caller.userId, gameId, request.expectedVersion, move).also { realtime.announce(it) },
            caller,
        )
    }

    post("/games/{gameId}/undo") {
        val caller = call.authenticatedUser()
        val gameId = call.gameId() ?: return@post

        val request = call.receive<UndoMoveRequest>()

        call.respondTo(
            commands.undoMove(caller.userId, gameId, request.expectedVersion).also { realtime.announce(it) },
            caller,
        )
    }

    post("/games/{gameId}/draw-claims") {
        val caller = call.authenticatedUser()
        val gameId = call.gameId() ?: return@post

        val request = call.receive<ClaimDrawRequest>()
        val claim = DrawClaim.entries.firstOrNull { it.name.equals(request.claim, ignoreCase = true) }

        if (claim == null) {
            call.respondText("Not a draw claim", status = HttpStatusCode.BadRequest)
            return@post
        }

        call.respondTo(
            commands.claimDraw(caller.userId, gameId, request.expectedVersion, claim).also { realtime.announce(it) },
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
            reject(
                HttpStatusCode.Conflict,
                RejectionReason.GAME_OVER,
                "This game has finished",
                result.game,
                caller,
            )

        is CommandResult.NotYourTurn ->
            reject(
                HttpStatusCode.Conflict,
                RejectionReason.NOT_YOUR_TURN,
                "It is not your move",
                result.game,
                caller,
            )

        is CommandResult.StaleVersion ->
            reject(
                HttpStatusCode.Conflict,
                RejectionReason.STALE_VERSION,
                "This game is at version ${result.game.version}",
                result.game,
                caller,
            )

        is CommandResult.IllegalMove ->
            reject(
                HttpStatusCode.UnprocessableEntity,
                RejectionReason.ILLEGAL_MOVE,
                "${result.move} is not legal here",
                result.game,
                caller,
            )

        is CommandResult.NoSuchClaim ->
            reject(
                HttpStatusCode.UnprocessableEntity,
                RejectionReason.NO_SUCH_CLAIM,
                "No ${result.claim} draw can be claimed here",
                result.game,
                caller,
            )

        is CommandResult.NothingToUndo ->
            reject(
                HttpStatusCode.Conflict,
                RejectionReason.NOTHING_TO_UNDO,
                "You have no move to take back",
                result.game,
                caller,
            )
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

/** Sends a refusal with the canonical state attached, so the caller can correct itself. */
private suspend fun ApplicationCall.reject(
    status: HttpStatusCode,
    reason: RejectionReason,
    message: String,
    game: StoredGame,
    caller: AuthenticatedUser,
) = respond(
    status,
    CommandRejection(
        reason = reason,
        message = message,
        game = GameView.of(game, caller.userId),
    ),
)

/**
 * Announces an accepted command to both players' open connections.
 *
 * Both sides are told, not only the opponent: the player who acted may have a second device
 * open, and the message is a nudge to reload rather than state, so the extra one costs
 * nothing. A refused command changed nothing, so there is nothing to announce.
 *
 * Delivery is best-effort and deliberately not part of accepting the command — a move is
 * accepted whether or not anyone is listening (`D022`).
 */
private suspend fun RealtimeHub.announce(result: CommandResult) {
    if (result !is CommandResult.Applied) return

    val game = result.game

    publish(
        userIds = listOf(game.whiteUserId, game.blackUserId),
        message = RealtimeMessage.gameUpdated(game.id, game.version),
    )
}
