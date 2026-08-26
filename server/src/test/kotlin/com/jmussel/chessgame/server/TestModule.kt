package com.jmussel.chessgame.server

import com.jmussel.chessgame.server.auth.SupabaseTokenVerifier
import com.jmussel.chessgame.server.db.DashboardQueries
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.game.GameCommandService
import com.jmussel.chessgame.server.realtime.RealtimeHub
import com.jmussel.chessgame.server.series.seriesService
import com.jmussel.chessgame.server.user.LastSeenTracker
import io.ktor.server.application.Application
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * The whole server over one test database.
 *
 * Wiring lives here so a new collaborator does not have to be threaded through every test
 * that only cares about one route.
 */
fun Application.testModule(
    verifier: SupabaseTokenVerifier,
    database: Database,
    lastSeen: LastSeenTracker? = null,
    realtime: RealtimeHub = RealtimeHub(),
) {
    val users = UserRepository(database)
    val series = seriesService(database)

    module(
        verifier = verifier,
        users = users,
        friendships = FriendshipRepository(database),
        series = series,
        dashboard = DashboardQueries(database),
        commands = GameCommandService(database, GameRepository(database), series),
        realtime = realtime,
        lastSeen = lastSeen ?: LastSeenTracker(users),
    )
}
