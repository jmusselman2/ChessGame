package com.jmussel.chessgame.api

import com.jmussel.chessgame.app.ChessAppDependencies
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * What happens to a realtime socket whose peer stops answering without closing (`M16.6`).
 *
 * This is the failure the two-device verification found: a free instance spins down
 * (`D032`) and the connection is never closed from the other end, so nothing arrives and
 * nothing ends either. `ChessAppViewModel.watchUpdates` reconnects when the message flow
 * *ends*, so a flow that never ends is a screen that never refreshes — a position minutes
 * behind, still saying "Your move" when the move is no longer the player's.
 *
 * A real socket is used rather than a stub, because the thing under test lives entirely in
 * the transport: nothing above `ChessRealtimeClient` can tell a quiet connection from a
 * dead one. The peer is hand-written for the same reason — a WebSocket library on the far
 * end would answer the pings by itself, which is exactly the behaviour that must be
 * absent. [SilentWebSocketPeer] completes the handshake, says one true thing, and then
 * never sends another byte.
 */
class SilentSocketTest {
    @Test
    fun aPeerThatStopsAnsweringWithoutClosingIsNoticed() {
        SilentWebSocketPeer().use { peer ->
            val received = collectUntilTheSocketEnds(peer, pingInterval = PING_INTERVAL)

            assertNotNull("a silent socket is noticed and the flow ends", received)
            assertEquals(
                "and it was a real connection that ended, not a handshake that failed",
                listOf(RealtimeMessageDto.CONNECTED),
                received?.map { it.type },
            )
        }
    }

    @Test
    fun withoutTheKeepaliveTheSameDeadSocketIsNeverNoticed() {
        SilentWebSocketPeer().use { peer ->
            // The app as it was: no ping interval, so nothing is ever sent and the flow
            // waits on a peer that will never speak again. Given many times the budget the
            // keepalive needs, it still does not end.
            val received = collectUntilTheSocketEnds(peer, pingInterval = Duration.ZERO)

            assertNull("without a keepalive the same dead socket is waited on forever", received)
        }
    }

    /**
     * Collects the realtime flow until it ends, or gives up after [BUDGET].
     *
     * Returns what arrived if the flow ended by itself, and `null` if it was still waiting
     * when the budget ran out — which is the defect, not a slow test.
     */
    private fun collectUntilTheSocketEnds(
        peer: SilentWebSocketPeer,
        pingInterval: Duration,
    ): List<RealtimeMessageDto>? =
        ChessAppDependencies.defaultHttpClient(pingInterval = pingInterval).use { httpClient ->
            val realtime =
                ChessRealtimeClient(
                    config = ChessServerConfig(baseUrl = peer.baseUrl, allowCleartext = true),
                    httpClient = httpClient,
                    accessToken = { "a-token" },
                )
            val received = CopyOnWriteArrayList<RealtimeMessageDto>()

            runBlocking {
                withTimeoutOrNull(BUDGET.inWholeMilliseconds) {
                    // A dead socket may end the flow either way round: OkHttp fails it when
                    // the pong does not come, and that surfaces as the collection throwing
                    // or simply finishing. Both mean the same thing here.
                    runCatching { realtime.messages().collect { received += it } }
                    received.toList()
                }
            }
        }

    private companion object {
        /**
         * Short enough that a test can wait out two of them, which is what detection costs:
         * an unanswered ping is noticed when the next one falls due. The app ships
         * `webSocketPingInterval` instead; the mechanism is the same either way.
         */
        val PING_INTERVAL = 300.milliseconds

        /** Many times the two periods detection needs, so only a real failure runs it out. */
        val BUDGET = 10_000.milliseconds
    }
}

/**
 * A WebSocket peer that accepts a connection, speaks once, and then goes silent forever.
 *
 * Deliberately hand-written and deliberately incomplete: it performs the handshake and
 * writes one text frame, and after that it answers nothing — no pong, no close frame, not
 * a byte. That silence is the whole point. It models a server that has gone away without
 * the connection being closed, which is what a spun-down free instance looks like from a
 * phone still holding the socket open.
 */
private class SilentWebSocketPeer : AutoCloseable {
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    private val spoken = CountDownLatch(1)

    @Volatile
    private var connection: Socket? = null

    val baseUrl: String get() = "http://${server.inetAddress.hostAddress}:${server.localPort}"

    private val listener =
        thread(isDaemon = true, name = "silent-websocket-peer") {
            runCatching {
                server.accept().also { connection = it }.use { socket ->
                    val key = handshake(socket)

                    socket.getOutputStream().apply {
                        write(upgradeResponse(key).toByteArray(Charsets.ISO_8859_1))
                        write(textFrame("{\"type\":\"${RealtimeMessageDto.CONNECTED}\"}"))
                        flush()
                    }
                    spoken.countDown()

                    // Nothing is read and nothing is written from here on. A client ping
                    // lands in the socket buffer and is never answered, which is the
                    // condition under test.
                    Thread.sleep(Long.MAX_VALUE)
                }
            }
        }

    /** Reads the client's request and returns its `Sec-WebSocket-Key`. */
    private fun handshake(socket: Socket): String {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
        var key: String? = null

        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith(KEY_HEADER, ignoreCase = true)) key = line.substringAfter(':').trim()
        }

        return requireNotNull(key) { "The client did not send a $KEY_HEADER header" }
    }

    /** The one response that makes a real client believe the socket is open. */
    private fun upgradeResponse(key: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest((key + WEBSOCKET_GUID).toByteArray(Charsets.US_ASCII))

        return buildString {
            append("HTTP/1.1 101 Switching Protocols\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Accept: ${Base64.getEncoder().encodeToString(digest)}\r\n")
            append("\r\n")
        }
    }

    /** One unmasked final text frame, which is all this peer ever sends. */
    private fun textFrame(payload: String): ByteArray {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        require(bytes.size < 126) { "This peer only ever sends one short message" }

        return byteArrayOf(FINAL_TEXT_FRAME, bytes.size.toByte()) + bytes
    }

    override fun close() {
        spoken.await(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)
        listener.interrupt()
        runCatching { connection?.close() }
        runCatching { server.close() }
    }

    private companion object {
        const val KEY_HEADER = "Sec-WebSocket-Key"
        const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        const val FINAL_TEXT_FRAME: Byte = 0x81.toByte()
        const val SHUTDOWN_GRACE_SECONDS = 1L
    }
}
