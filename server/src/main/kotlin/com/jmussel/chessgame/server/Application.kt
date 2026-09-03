@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server

import com.jmussel.chessgame.core.GameCore
import com.jmussel.chessgame.server.auth.SUPABASE_AUTH
import com.jmussel.chessgame.server.auth.SupabaseTokenVerifier
import com.jmussel.chessgame.server.auth.installSupabaseAuthentication
import com.jmussel.chessgame.server.dashboard.dashboardRoutes
import com.jmussel.chessgame.server.db.DashboardQueries
import com.jmussel.chessgame.server.db.DatabaseConfig
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.HistoryQueries
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.friends.friendRoutes
import com.jmussel.chessgame.server.game.GameCommandService
import com.jmussel.chessgame.server.game.gameRoutes
import com.jmussel.chessgame.server.history.historyRoutes
import com.jmussel.chessgame.server.realtime.RealtimeHub
import com.jmussel.chessgame.server.realtime.installRealtimeWebSockets
import com.jmussel.chessgame.server.realtime.realtimeRoutes
import com.jmussel.chessgame.server.series.SeriesService
import com.jmussel.chessgame.server.series.seriesRoutes
import com.jmussel.chessgame.server.user.LastSeenTracker
import com.jmussel.chessgame.server.user.identityRoutes
import com.jmussel.chessgame.server.user.userLookupRoutes
import com.jmussel.chessgame.server.user.usernameRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlin.uuid.ExperimentalUuidApi

private const val SUPABASE_URL = "SUPABASE_URL"

/** The variable a host uses to tell the process which port to listen on. */
private const val PORT = "PORT"

/** The port used when nothing sets [PORT]; the local one in `docs/DEVELOPMENT.md`. */
const val DEFAULT_PORT: Int = 8080

private const val MAX_PORT = 65535

fun main() {
    val databaseConfig = DatabaseConfig.fromEnvironmentOrNull()
    val supabaseUrl = System.getenv(SUPABASE_URL)?.takeIf { it.isNotBlank() }

    embeddedServer(
        factory = Netty,
        port = serverPort(System.getenv(PORT)),
        // Every address in the container, not just loopback: a host routes to the process
        // from outside it, and a server bound to 127.0.0.1 is unreachable from the proxy.
        host = "0.0.0.0",
    ) {
        if (databaseConfig == null || supabaseUrl == null) {
            // Nothing to authenticate against and nowhere to store a game, so serve the
            // health check alone rather than failing to start. See docs/DEVELOPMENT.md.
            log.warn(
                "Starting with /health only: set ${DatabaseConfig.DATABASE_URL} and $SUPABASE_URL " +
                    "to serve the authenticated API.",
            )
            healthModule()
        } else {
            // Realtime delivery is process-local (ARCHITECTURE.md 12, D032), so this has to
            // be the only instance. Said at startup because that is where an operator scaling
            // the service will see it.
            log.info("Realtime delivery is in-process: run exactly one instance of this server.")
            val database = Databases.connectAndMigrate(databaseConfig.dataSource())
            val users = UserRepository(database)
            val games = GameRepository(database)
            val series =
                SeriesService(
                    database = database,
                    series = GameSeriesRepository(database),
                    games = games,
                )
            module(
                verifier = SupabaseTokenVerifier.forProject(supabaseUrl),
                users = users,
                friendships = FriendshipRepository(database),
                series = series,
                dashboard = DashboardQueries(database),
                history = HistoryQueries(database),
                commands = GameCommandService(database, games, series),
            )
        }
    }.start(wait = true)
}

/**
 * The server's routes.
 *
 * `/health` is open; everything else sits behind a verified Supabase token, which resolves
 * to the internal user id the rest of the database references.
 */
fun Application.module(
    verifier: SupabaseTokenVerifier,
    users: UserRepository,
    friendships: FriendshipRepository,
    series: SeriesService,
    dashboard: DashboardQueries,
    history: HistoryQueries,
    commands: GameCommandService,
    realtime: RealtimeHub = RealtimeHub(),
    lastSeen: LastSeenTracker = LastSeenTracker(users),
) {
    installRequestLogging()
    install(ContentNegotiation) { json() }
    installRealtimeWebSockets()
    installSupabaseAuthentication(verifier, users, lastSeen)

    routing {
        healthRoute()

        authenticate(SUPABASE_AUTH) {
            identityRoutes(users)
            usernameRoutes(users)
            userLookupRoutes(users)
            friendRoutes(users, friendships)
            seriesRoutes(users, friendships, series, realtime)
            dashboardRoutes(dashboard)
            historyRoutes(history)
            gameRoutes(commands, realtime, users)
            realtimeRoutes(realtime)
        }
    }
}

/** The health endpoint alone, for a server started without a database or auth. */
fun Application.healthModule() {
    routing { healthRoute(healthOnly = true) }
}

private fun Route.healthRoute(healthOnly: Boolean = false) {
    get("/health") {
        call.respondText(
            text = healthText(healthOnly),
            status = HttpStatusCode.OK,
        )
    }
}

/**
 * The port to listen on.
 *
 * A host tells the process where to listen through `PORT` — Render picks `10000` by
 * default and fails the deploy when nothing binds the port it chose — so the environment
 * wins whenever it says anything and [DEFAULT_PORT] is only the local fallback.
 *
 * A value that is not a port is a failure rather than a fallback. Quietly listening on
 * [DEFAULT_PORT] instead would look like a clean start and then fail as an unexplained
 * "no open ports detected" once the host gave up waiting on the port it asked for.
 */
fun serverPort(value: String?): Int {
    val requested = value?.trim()?.takeIf { it.isNotEmpty() } ?: return DEFAULT_PORT
    val port = requested.toIntOrNull()

    require(port != null && port in 1..MAX_PORT) { "$PORT is not a port number: $requested" }

    return port
}

/**
 * What `/health` says about itself.
 *
 * A server with no database and no auth still answers this endpoint, and a host watching
 * it will call that a successful deploy. So the body names the degraded mode: the status
 * alone cannot distinguish a working beta from one whose environment was never filled in,
 * and that difference is worth one curl rather than a confused play-through.
 */
fun healthText(healthOnly: Boolean): String =
    if (healthOnly) {
        "${GameCore.NAME} server is healthy (health-only: ${DatabaseConfig.DATABASE_URL} " +
            "and $SUPABASE_URL are not set)"
    } else {
        "${GameCore.NAME} server is healthy"
    }
