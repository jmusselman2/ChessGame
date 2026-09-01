package com.jmussel.chessgame.server.realtime

import io.ktor.server.application.plugin
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * That the realtime transport actually keeps its connections alive.
 *
 * A game is silent while a player thinks, and a hosted connection crosses proxies that
 * close a silent socket. The values are asserted against the installed plugin rather than
 * against the constants, so removing the configuration fails here instead of in a beta
 * game that stops receiving moves (`M15.2`).
 */
class WebSocketKeepAliveTest {
    @Test
    fun theRealtimeTransportPingsAndTimesOut() =
        testApplication {
            application {
                installRealtimeWebSockets()

                val websockets = plugin(WebSockets)

                assertEquals(webSocketPingPeriod.inWholeMilliseconds, websockets.pingIntervalMillis)
                assertEquals(webSocketPongTimeout.inWholeMilliseconds, websockets.timeoutMillis)
            }

            // Starts the application, so the assertions above run.
            startApplication()
        }
}
