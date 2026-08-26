@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.realtime

import com.jmussel.chessgame.server.auth.authenticatedUser
import io.ktor.server.routing.Route
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
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
 */
fun Route.realtimeRoutes(hub: RealtimeHub) {
    webSocket("/ws") {
        val caller = call.authenticatedUser()
        val connection = RealtimeConnection { message -> sendSerialized(message) }

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
