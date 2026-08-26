@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.dashboard

import com.jmussel.chessgame.server.api.DashboardEntry
import com.jmussel.chessgame.server.auth.authenticatedUser
import com.jmussel.chessgame.server.db.DashboardQueries
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.uuid.ExperimentalUuidApi

/**
 * The dashboard a returning player lands on.
 *
 * One request answers "what am I playing and whose move is it" for every active series at
 * once, so the app does not have to ask per friend.
 *
 * Routes must sit behind authentication.
 */
fun Route.dashboardRoutes(dashboard: DashboardQueries) {
    get("/dashboard") {
        val caller = call.authenticatedUser()

        call.respond(dashboard.activeSeriesFor(caller.userId).mapNotNull(DashboardEntry::of))
    }
}
