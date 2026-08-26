@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.history

import com.jmussel.chessgame.server.api.SeriesHistoryEntry
import com.jmussel.chessgame.server.auth.authenticatedUser
import com.jmussel.chessgame.server.db.HistoryQueries
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.uuid.ExperimentalUuidApi

/**
 * Reading what has already been played.
 *
 * History is read-only, and it is read-only by construction rather than by a rule stated
 * here: a finished game refuses every command (`D017`) and a closed series never gets
 * another one (`D012`, `D013`), so there is nothing to write and no endpoint that would.
 * The caller only ever sees series they took part in.
 *
 * Routes must sit behind authentication.
 */
fun Route.historyRoutes(history: HistoryQueries) {
    get("/history") {
        val caller = call.authenticatedUser()

        call.respond(history.historyFor(caller.userId).mapNotNull(SeriesHistoryEntry::of))
    }
}
