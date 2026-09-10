@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.event.Level
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * What the server writes down about a request.
 *
 * Enough to work out what happened — the method, the path, the status — and nothing that
 * would be dangerous to keep. Headers are never logged, so the bearer token that is on
 * every authenticated request cannot leak into a log file, and neither can the Supabase
 * key. Bodies are never logged either: a move is small, but a log full of them is a copy
 * of the game state living somewhere with weaker access rules than the database.
 *
 * Ids in a path stay as they are. A game id or a user id is a reference, not a secret, and
 * without them a log cannot answer "what happened to this game", which is the whole reason
 * to keep one.
 *
 * ### What level a failure gets (`M19.12`)
 *
 * The rule is **signal, not severity**: a line reaches `INFO` when it means something
 * actually went wrong for a player, and stays at `DEBUG` when it is only detail.
 *
 * | | level | why |
 * |---|---|---|
 * | A realtime send that failed | `INFO` | one player did not get one update |
 * | A realtime send that timed out | `WARN` | a socket that neither delivers nor fails — the `M12-01` pathology |
 * | A refused command | `INFO` | "asked at version 7, game was at 8" is a complete account |
 * | An accepted command | `DEBUG` | already an audit event (`D020`); a busy game would fill the log |
 * | A rejected bearer token | `INFO` | one is a stale session, a run of them is worth looking at |
 * | A socket opening, closing, or ending by exception | `DEBUG` | an ordinary client close reaches either path depending on timing, so it carries no signal |
 * | An error that escapes a route | `ERROR` | Ktor's own handler; nothing added |
 *
 * **Never logged**, whatever the level: headers, bodies, and the *message* of a rejected
 * token. That last one is the non-obvious case — the JWT library quotes malformed input
 * back in its own exception messages, so relaying one could put credential material in a
 * log file. The route is enough to find the caller.
 */
fun Application.installRequestLogging() {
    install(CallLogging) {
        level = Level.INFO

        // The health check is polled by whatever is watching the server; logging it would
        // bury everything else.
        filter { call -> call.request.path() != HEALTH_PATH }

        format { call -> "${call.request.httpMethod.value} ${call.request.path()} -> ${statusOf(call)}" }
    }
}

/**
 * One line about a command the server decided on.
 *
 * The version and the outcome are what make a log useful afterwards: "this player asked to
 * move at version 7 and was told the game was already at 8" is a complete account of a
 * refusal, and no part of it is a secret.
 */
fun commandLogLine(
    action: String,
    userId: Uuid,
    gameId: Uuid,
    expectedVersion: Long,
    outcome: String,
): String = "$action user=$userId game=$gameId expectedVersion=$expectedVersion outcome=$outcome"

private fun statusOf(call: ApplicationCall): String {
    val status = call.response.status()

    return status?.value?.toString() ?: "unhandled"
}

private const val HEALTH_PATH = "/health"
