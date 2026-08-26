@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server

import com.jmussel.chessgame.core.GameCore
import com.jmussel.chessgame.server.auth.SUPABASE_AUTH
import com.jmussel.chessgame.server.auth.SupabaseTokenVerifier
import com.jmussel.chessgame.server.auth.authenticatedUser
import com.jmussel.chessgame.server.auth.installSupabaseAuthentication
import com.jmussel.chessgame.server.db.DatabaseConfig
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.friends.friendRoutes
import com.jmussel.chessgame.server.series.seriesRoutes
import com.jmussel.chessgame.server.user.LastSeenTracker
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

fun main() {
    val databaseConfig = DatabaseConfig.fromEnvironmentOrNull()
    val supabaseUrl = System.getenv(SUPABASE_URL)?.takeIf { it.isNotBlank() }

    embeddedServer(
        factory = Netty,
        port = 8080,
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
            val database = Databases.connectAndMigrate(databaseConfig.dataSource())
            val users = UserRepository(database)
            module(
                verifier = SupabaseTokenVerifier.forProject(supabaseUrl),
                users = users,
                friendships = FriendshipRepository(database),
                series = GameSeriesRepository(database),
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
    series: GameSeriesRepository,
    lastSeen: LastSeenTracker = LastSeenTracker(users),
) {
    install(ContentNegotiation) { json() }
    installSupabaseAuthentication(verifier, users, lastSeen)

    routing {
        healthRoute()

        authenticate(SUPABASE_AUTH) {
            get("/me") {
                call.respondText(call.authenticatedUser().userId.toString(), status = HttpStatusCode.OK)
            }

            usernameRoutes(users)
            userLookupRoutes(users)
            friendRoutes(users, friendships)
            seriesRoutes(users, friendships, series)
        }
    }
}

/** The health endpoint alone, for a server started without a database or auth. */
fun Application.healthModule() {
    routing { healthRoute() }
}

private fun Route.healthRoute() {
    get("/health") {
        call.respondText(
            text = "${GameCore.NAME} server is healthy",
            status = HttpStatusCode.OK,
        )
    }
}
