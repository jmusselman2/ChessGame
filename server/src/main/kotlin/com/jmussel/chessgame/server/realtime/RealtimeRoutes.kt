@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.realtime

import com.jmussel.chessgame.server.auth.authenticatedUser
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.Route
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

/**
 * The realtime connection.
 *
 * One authenticated socket per client carries updates for every game that client plays in,
 * so the app does not open a connection per game. Nothing is *decided* here: the socket
 * says "this game changed, it is at this version" and the client reloads the canonical
 * state over HTTPS (`D022`).
 *
 * The route must sit behind authentication — an anonymous socket has no games to hear
 * about.
 *
 * ### Reconnecting
 *
 * Nothing is replayed to a client that was away, and nothing needs to be: whatever it
 * missed is already in the canonical state it reloads over HTTPS (`D022`). The connection
 * is registered *before* the greeting goes out, which is what makes that reload safe — a
 * client that reloads after seeing `connected` cannot fall into a gap, because any change
 * committed from that moment on is pushed to this socket, and every earlier one is in the
 * reload. Greeting first and subscribing after would open exactly that gap.
 */
fun Route.realtimeRoutes(hub: RealtimeHub) {
    webSocket("/ws") {
        val caller = call.authenticatedUser()
        val connection = RealtimeConnection { message -> sendSerialized(message) }

        // Before the greeting, so a client that reloads on `connected` misses nothing.
        hub.subscribe(caller.userId, connection)

        try {
            sendSerialized(RealtimeMessage.connected())

            // Nothing is expected from the client; commands go over HTTPS. Reading keeps
            // the connection open and lets a close from the other end land here.
            for (frame in incoming) {
                if (frame is Frame.Close) break
            }
        } finally {
            hub.unsubscribe(caller.userId, connection)
            close(CloseReason(CloseReason.Codes.NORMAL, "Bye"))
        }
    }
}

/**
 * How often the server pings an open WebSocket.
 *
 * A game is idle for as long as a player thinks, and a connection that carries nothing in
 * that time is indistinguishable from a dead one to whatever sits between the client and
 * here. Thirty seconds stays well inside the idle windows a hosted connection crosses.
 */
val webSocketPingPeriod: Duration = 30.seconds

/**
 * How long a client has to answer a ping before the server closes its connection.
 *
 * Closing is the right outcome rather than a loss: the client reconnects and reloads
 * canonical state over HTTPS (`D022`), whereas a half-open connection would stay in the
 * hub and quietly swallow every update sent to it.
 */
val webSocketPongTimeout: Duration = 60.seconds

/**
 * The WebSocket transport the realtime route runs on.
 *
 * Separate from the route so the keepalive above is one decision in one place, and so a
 * test can assert the server is actually configured with it.
 */
fun Application.installRealtimeWebSockets() {
    install(WebSockets) {
        pingPeriodMillis = webSocketPingPeriod.inWholeMilliseconds
        timeoutMillis = webSocketPongTimeout.inWholeMilliseconds
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }
}
